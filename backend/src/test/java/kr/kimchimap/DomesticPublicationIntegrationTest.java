package kr.kimchimap;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import kr.kimchimap.auth.service.JwtService;
import kr.kimchimap.auth.service.SessionService;
import kr.kimchimap.global.web.ApiException;
import kr.kimchimap.ingestion.client.PublicDataRestaurantClient;
import kr.kimchimap.ingestion.repository.IngestionJobRepository;
import kr.kimchimap.ingestion.service.IngestionJobService;
import kr.kimchimap.ingestion.service.IngestionWorker;
import kr.kimchimap.member.service.MemberService;
import kr.kimchimap.search.dto.SearchRequest;
import kr.kimchimap.search.service.MapSearchService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@TestPropertySource(properties = "app.ingestion.public-data-key=test-never-sent")
class DomesticPublicationIntegrationTest extends ApplicationIntegrationSupport {
  @Autowired PlatformTransactionManager transactions;
  @Autowired IngestionJobService jobs;
  @Autowired IngestionJobRepository jobRepository;
  @Autowired IngestionWorker worker;
  @Autowired MapSearchService search;
  @Autowired MemberService members;
  @Autowired SessionService sessions;
  @Autowired JwtService jwt;
  @MockitoBean PublicDataRestaurantClient external;

  @Test
  void publicReadAndBookmarkRequireDomesticEvidenceAndRespectWithdrawal() throws Exception {
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO app.restaurant(id,name,address,business_status,coordinate_status,published,location)
        VALUES (?,'가상 테스트 공개 자격 업소','테스트 주소','OPEN','VERIFIED',true,
          public.ST_SetSRID(public.ST_MakePoint(129.1,35.2),4326))
        """,
        id);
    var request =
        new SearchRequest(
            new SearchRequest.Center(35.2, 129.1), null, 100d, List.of(), null, null, 20, null);
    String token = token(false);
    assertThat(search.search(request).items()).isEmpty();
    assertThat(get("/api/v1/restaurants/" + id).statusCode()).isEqualTo(404);
    assertThat(call("PUT", "/api/v1/bookmarks/" + id, token, null).statusCode()).isEqualTo(404);
    UUID record = DomesticOriginFixture.addRice(jdbc, transactions, id);
    assertThat(search.search(request).items()).extracting(item -> item.id()).containsExactly(id);
    assertThat(get("/api/v1/restaurants/" + id).statusCode()).isEqualTo(200);
    assertThat(call("PUT", "/api/v1/bookmarks/" + id, token, null).statusCode()).isEqualTo(204);
    assertThat(call("GET", "/api/v1/bookmarks", token, null).body()).contains(id.toString());
    jdbc.update(
        "INSERT INTO app.origin_withdrawal(record_id,reason,actor_reference,withdrawn_at) VALUES (?,'테스트 마지막 국내산 근거 철회','test-admin',CURRENT_TIMESTAMP)",
        record);
    assertThat(search.search(request).items()).isEmpty();
    assertThat(get("/api/v1/restaurants/" + id).statusCode()).isEqualTo(404);
    assertThat(call("GET", "/api/v1/bookmarks", token, null).body()).doesNotContain(id.toString());
  }

  @Test
  void actualPolicyRejectsManualScheduledAndPreviouslyQueuedOriginlessCollection()
      throws Exception {
    assertThatThrownBy(() -> jobs.request("INCREMENTAL", 100, 1))
        .isInstanceOfSatisfying(
            ApiException.class, error -> assertThat(error.status().value()).isEqualTo(409));
    var before = jobs.recent().size();
    jobs.scheduleDue();
    assertThat(jobs.recent()).hasSize(before);
    UUID old =
        new TransactionTemplate(transactions)
            .execute(
                status -> {
                  jobRepository.lockSource();
                  return jobRepository.create(
                      "INCREMENTAL", Instant.now().minusSeconds(86400), Instant.now(), 100, 1);
                });
    assertThat(worker.processOne(old)).isTrue();
    assertThat(jobs.get(old).status()).isEqualTo("FAILED");
    assertThat(jobs.get(old).errorCode()).isEqualTo("ORIGIN_QUALIFIED_SOURCE_REQUIRED");
    String admin = token(true);
    var response =
        call(
            "POST",
            "/api/v1/admin/ingestion/sources/"
                + IngestionJobRepository.PUBLIC_DATA_SOURCE
                + "/runs",
            admin,
            "{\"mode\":\"INCREMENTAL\",\"pageBudget\":1,\"reason\":\"테스트 차단 확인\"}");
    assertThat(response.statusCode()).isEqualTo(409);
    assertThat(response.body()).contains("ORIGIN_QUALIFIED_SOURCE_REQUIRED");
    assertThat(call("GET", "/api/v1/admin/ingestion/sources", admin, null).body())
        .contains("\"domesticQualificationSupported\":false", "\"scheduled\":false");
    verifyNoInteractions(external);
  }

  private String token(boolean admin) {
    var member = members.loginWithVerifiedKakaoSubject("test-domestic-" + UUID.randomUUID());
    if (admin) members.changeSecurity(member.id(), "ADMIN", "ACTIVE", "테스트 관리자", "test-fixture");
    return jwt.issue(sessions.create(members.requireActive(member.id())).session());
  }

  private HttpResponse<String> call(String method, String path, String token, String body)
      throws Exception {
    return client.send(
        HttpRequest.newBuilder(URI.create(base() + path))
            .header("Authorization", "Bearer " + token)
            .header("Content-Type", "application/json")
            .header("Idempotency-Key", UUID.randomUUID().toString())
            .method(
                method,
                body == null
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(body))
            .build(),
        HttpResponse.BodyHandlers.ofString());
  }
}
