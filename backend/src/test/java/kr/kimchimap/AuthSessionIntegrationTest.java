package kr.kimchimap;

import static org.assertj.core.api.Assertions.*;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import kr.kimchimap.auth.service.JwtService;
import kr.kimchimap.auth.service.SessionService;
import kr.kimchimap.auth.service.TokenSecrets;
import kr.kimchimap.global.web.ApiException;
import kr.kimchimap.member.service.MemberService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.json.JsonMapper;

class AuthSessionIntegrationTest extends ApplicationIntegrationSupport {
  @Autowired MemberService members;
  @Autowired SessionService sessions;
  @Autowired JwtService jwt;
  private final JsonMapper mapper = JsonMapper.builder().build();

  @Test
  void issuedTokensRotateWithoutPlaintextStorageAndReplayRevokesOnlyKnownFamily() throws Exception {
    var member = members.loginWithVerifiedKakaoSubject("test-" + UUID.randomUUID());
    assertThat(member.role()).isEqualTo("USER");
    var issued = sessions.create(member);
    var other = sessions.create(member);
    assertThat(me(jwt.issue(issued.session())).statusCode()).isEqualTo(200);
    String hash = TokenSecrets.hash(issued.refreshToken());
    assertThat(redis.opsForValue().get("km:{auth}:token:" + hash))
        .isEqualTo(issued.session().id().toString());
    assertThat(redis.opsForHash().entries("km:{auth}:session:" + issued.session().id()).values())
        .doesNotContain(issued.refreshToken());
    assertThat(redis.getExpire("km:{auth}:token:" + hash))
        .isBetween(1L, Duration.ofDays(14).toSeconds());
    assertThatThrownBy(() -> sessions.rotate(TokenSecrets.generate()))
        .isInstanceOf(ApiException.class);
    assertThat(sessions.requireActive(issued.session().id())).isEqualTo(issued.session());
    var next = sessions.rotate(issued.refreshToken());
    assertThat(next.refreshToken()).isNotEqualTo(issued.refreshToken());
    assertThat(next.session().expiresAt()).isEqualTo(issued.session().expiresAt());
    assertThatThrownBy(() -> sessions.rotate(issued.refreshToken()))
        .isInstanceOf(ApiException.class);
    assertThat(me(jwt.issue(next.session())).statusCode()).isEqualTo(401);
    assertThat(sessions.requireActive(other.session().id())).isEqualTo(other.session());
  }

  @Test
  void simultaneousRefreshHasOneWinnerAndStrictReplayInvalidatesFamily() throws Exception {
    var issued =
        sessions.create(members.loginWithVerifiedKakaoSubject("test-" + UUID.randomUUID()));
    var start = new CountDownLatch(1);
    Callable<Boolean> rotate =
        () -> {
          start.await();
          try {
            sessions.rotate(issued.refreshToken());
            return true;
          } catch (ApiException exception) {
            return false;
          }
        };
    try (var executor = Executors.newFixedThreadPool(2)) {
      var first = executor.submit(rotate);
      var second = executor.submit(rotate);
      start.countDown();
      assertThat(List.of(first.get(), second.get())).containsExactlyInAnyOrder(true, false);
    }
    assertThatThrownBy(() -> sessions.requireActive(issued.session().id()))
        .isInstanceOf(ApiException.class);
  }

  @Test
  void currentAndAllSessionLogoutRevokeExistingAccessTokens() throws Exception {
    var member = members.loginWithVerifiedKakaoSubject("test-" + UUID.randomUUID());
    var first = sessions.create(member);
    var second = sessions.create(member);
    String firstAccess = jwt.issue(first.session()), secondAccess = jwt.issue(second.session());
    sessions.logout(first.refreshToken());
    assertThat(me(firstAccess).statusCode()).isEqualTo(401);
    assertThat(me(secondAccess).statusCode()).isEqualTo(200);
    sessions.revokeAll(member.id());
    assertThat(me(secondAccess).statusCode()).isEqualTo(401);
    assertThatThrownBy(() -> sessions.rotate(second.refreshToken()))
        .isInstanceOf(ApiException.class);
  }

  @Test
  void currentMemberRoleStatusAndWithdrawalOverrideOldJwt() throws Exception {
    var member = members.loginWithVerifiedKakaoSubject("test-" + UUID.randomUUID());
    var session = sessions.create(member);
    String access = jwt.issue(session.session());
    assertThat(
            request("GET", "/api/v1/admin/ingestion/jobs", access, null, null, null).statusCode())
        .isEqualTo(403);
    members.changeSecurity(member.id(), "ADMIN", "ACTIVE", "테스트 권한 변경", "test-operator");
    assertThat(me(access).statusCode()).isEqualTo(401);
    var admin = sessions.create(members.requireActive(member.id()));
    assertThat(me(jwt.issue(admin.session())).body()).contains("ADMIN");
    members.changeSecurity(member.id(), "ADMIN", "SUSPENDED", "테스트 정지", "test-operator");
    assertThat(me(jwt.issue(admin.session())).statusCode()).isEqualTo(401);
    assertThatThrownBy(() -> sessions.rotate(admin.refreshToken()))
        .isInstanceOf(ApiException.class);
    members.changeSecurity(member.id(), "USER", "ACTIVE", "테스트 해제", "test-operator");
    var restored = sessions.create(members.requireActive(member.id()));
    sessions.withdraw(member.id());
    assertThat(me(jwt.issue(restored.session())).statusCode()).isEqualTo(401);
    assertThat(
            jdbc.queryForObject(
                "SELECT provider_subject FROM app.member WHERE id=?", String.class, member.id()))
        .isNull();
  }

  @Test
  void refreshRequiresCsrfAndAllowedOriginAndUsesRestrictedHttpOnlyCookie() throws Exception {
    var issued =
        sessions.create(members.loginWithVerifiedKakaoSubject("test-" + UUID.randomUUID()));
    String refresh = "km-local-refresh=" + issued.refreshToken();
    assertThat(
            request("POST", "/api/v1/auth/refresh", null, refresh, null, "http://localhost:5173")
                .statusCode())
        .isEqualTo(403);
    var csrf = get("/api/v1/auth/csrf");
    String token = mapper.readTree(csrf.body()).path("token").asString();
    String cookie = csrf.headers().firstValue("set-cookie").orElseThrow().split(";", 2)[0];
    assertThat(
            request(
                    "POST",
                    "/api/v1/auth/refresh",
                    null,
                    cookie + "; " + refresh,
                    token,
                    "https://untrusted.invalid")
                .statusCode())
        .isEqualTo(403);
    var response =
        request(
            "POST",
            "/api/v1/auth/refresh",
            null,
            cookie + "; " + refresh,
            token,
            "http://localhost:5173");
    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.headers().firstValue("cache-control")).hasValue("no-store");
    String setCookie =
        response.headers().allValues("set-cookie").stream()
            .filter(c -> c.startsWith("km-local-refresh="))
            .findFirst()
            .orElseThrow();
    assertThat(setCookie)
        .contains("HttpOnly", "SameSite=Lax", "Path=/api/v1/auth")
        .doesNotContain("Domain=", issued.refreshToken());
    String access = mapper.readTree(response.body()).path("accessToken").asString();
    assertThat(me(access).statusCode()).isEqualTo(200);
    assertThat(
            request(
                    "POST",
                    "/api/v1/auth/logout-all",
                    access,
                    cookie,
                    token,
                    "http://localhost:5173")
                .statusCode())
        .isEqualTo(204);
    assertThat(me(access).statusCode()).isEqualTo(401);
  }

  @Test
  void redisDataLossDoesNotRestoreSessionFromSignedJwt() throws Exception {
    var issued =
        sessions.create(members.loginWithVerifiedKakaoSubject("test-" + UUID.randomUUID()));
    String access = jwt.issue(issued.session());
    redis.delete("km:{auth}:session:" + issued.session().id());
    assertThat(me(access).statusCode()).isEqualTo(401);
    assertThatThrownBy(() -> sessions.rotate(issued.refreshToken()))
        .isInstanceOf(ApiException.class);
    assertThat(get("/api/v1/catalogs/ingredients").statusCode()).isEqualTo(200);
  }

  private HttpResponse<String> me(String token) throws Exception {
    return request("GET", "/api/v1/members/me", token, null, null, null);
  }

  @Test
  void sessionLimitCanBeRecoveredByLogoutAndRefreshRateIsBounded() {
    var member = members.loginWithVerifiedKakaoSubject("test-" + UUID.randomUUID());
    for (int i = 0; i < 20; i++) sessions.create(member);
    assertThatThrownBy(() -> sessions.create(member)).isInstanceOf(ApiException.class);
    sessions.revokeAll(member.id());
    assertThat(sessions.create(member).session().memberId()).isEqualTo(member.id());
    String address = "test-" + UUID.randomUUID();
    for (int i = 0; i < 20; i++) assertThat(sessions.allowRefresh(address)).isTrue();
    assertThat(sessions.allowRefresh(address)).isFalse();
  }

  @Test
  void redisOutageFailsClosedWhilePublicCatalogRemainsAvailable() throws Exception {
    var issued =
        sessions.create(members.loginWithVerifiedKakaoSubject("test-" + UUID.randomUUID()));
    String access = jwt.issue(issued.session());
    var docker = org.testcontainers.DockerClientFactory.instance().client();
    docker.pauseContainerCmd(REDIS.getContainerId()).exec();
    try {
      var denied = me(access);
      assertThat(denied.statusCode()).isEqualTo(503);
      assertThat(denied.body())
          .contains("AUTHENTICATION_UNAVAILABLE")
          .doesNotContain("RedisCommand", access);
      assertThat(get("/api/v1/catalogs/ingredients").statusCode()).isEqualTo(200);
    } finally {
      docker.unpauseContainerCmd(REDIS.getContainerId()).exec();
    }
  }

  private HttpResponse<String> request(
      String method, String path, String bearer, String cookie, String csrf, String origin)
      throws Exception {
    var request =
        HttpRequest.newBuilder(URI.create(base() + path))
            .method(method, HttpRequest.BodyPublishers.noBody());
    if (bearer != null) request.header("Authorization", "Bearer " + bearer);
    if (cookie != null) request.header("Cookie", cookie);
    if (csrf != null) request.header("X-CSRF-TOKEN", csrf);
    if (origin != null) request.header("Origin", origin);
    return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
  }
}
