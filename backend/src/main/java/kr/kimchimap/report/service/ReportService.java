package kr.kimchimap.report.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import kr.kimchimap.auth.service.TokenSecrets;
import kr.kimchimap.global.web.ApiException;
import kr.kimchimap.media.service.MediaService;
import kr.kimchimap.origin.dto.OriginPublicationRequest;
import kr.kimchimap.origin.entity.OriginValue;
import kr.kimchimap.origin.service.OriginPublicationService;
import kr.kimchimap.report.dto.*;
import kr.kimchimap.report.repository.ReportRateRepository;
import kr.kimchimap.report.repository.ReportRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class ReportService {
  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
  private static final Set<String> STATES =
      Set.of("PENDING", "APPROVED", "REJECTED", "NEEDS_MORE_INFO", "WITHDRAWN");
  private final ReportRepository reports;
  private final ReportRateRepository rate;
  private final MediaService media;
  private final OriginPublicationService origins;
  private final ObjectMapper json;
  private final Clock clock;

  public ReportService(
      ReportRepository reports,
      ReportRateRepository rate,
      MediaService media,
      OriginPublicationService origins,
      ObjectMapper json,
      Clock clock) {
    this.reports = reports;
    this.rate = rate;
    this.media = media;
    this.origins = origins;
    this.json = json;
    this.clock = clock;
  }

  @Transactional
  public ReportView create(UUID owner, UUID requestKey, ReportSubmission input) {
    reports.lockRequest(owner, requestKey);
    String body = json.writeValueAsString(input), hash = TokenSecrets.hash(body);
    var repeated = reports.repeated(owner, requestKey);
    if (repeated.isPresent()) {
      if (!repeated.get().requestHash().equals(hash))
        throw new ApiException(
            HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT", "같은 요청 키에 다른 제보 내용이 전달되었습니다.");
      return ownDetail(owner, repeated.get().reportId());
    }
    validate(input);
    limit(owner);
    media.attach(owner, input.mediaIds());
    UUID id = UUID.randomUUID(), revision = UUID.randomUUID();
    reports.create(id, owner, input.restaurantId(), revision);
    reports.revision(revision, id, body, input.mediaIds());
    reports.remember(owner, requestKey, hash, id);
    return view(reports.find(id, false).orElseThrow());
  }

  @Transactional
  public ReportView update(UUID owner, UUID id, ReportChanges.Update change) {
    var row = owned(owner, id, true);
    checkVersion(row, change.expectedVersion());
    editable(row);
    limit(owner);
    var input = change.submission();
    validate(input);
    if (!input.restaurantId().equals(row.restaurantId())) throw invalid("수정할 제보의 업소를 변경할 수 없습니다.");
    media.attach(owner, input.mediaIds());
    UUID revision = UUID.randomUUID();
    reports.revision(revision, id, json.writeValueAsString(input), input.mediaIds());
    reports.update(id, revision, "PENDING");
    return view(reports.find(id, false).orElseThrow());
  }

  @Transactional
  public ReportView withdraw(UUID owner, UUID id, ReportChanges.Withdraw change) {
    var row = owned(owner, id, true);
    checkVersion(row, change.expectedVersion());
    editable(row);
    limit(owner);
    reports.review(
        id,
        row.currentRevisionId(),
        owner,
        "WITHDRAWN",
        change.reason(),
        row.version(),
        null,
        List.of());
    reports.update(id, row.currentRevisionId(), "WITHDRAWN");
    return view(reports.find(id, false).orElseThrow());
  }

  @Transactional
  public ReportView review(UUID actor, UUID id, ReportChanges.Review change) {
    var row = reports.find(id, true).orElseThrow(ReportService::missing);
    checkVersion(row, change.expectedVersion());
    if (!row.state().equals("PENDING")) throw conflict("이미 검토되었거나 철회된 제보입니다.");
    var input = json.readValue(row.body(), ReportSubmission.class);
    var publicMedia = change.privacyReviewedMediaIds();
    if (new HashSet<>(publicMedia).size() != publicMedia.size()
        || !input.mediaIds().containsAll(publicMedia))
      throw invalid("검토 중인 제보의 사진만 공개 대상으로 선택해 주세요.");
    UUID scope = null;
    if (change.decision().equals("APPROVED")) {
      media.publishReviewed(row.ownerId(), publicMedia, actor);
      var assertions =
          input.claims().stream()
              .map(
                  claim ->
                      new OriginPublicationRequest.Assertion(
                          claim.ingredientId(), claim.originalExpression(), value(claim)))
              .toList();
      scope =
          origins.approve(
              new OriginPublicationRequest(
                  row.restaurantId(),
                  change.approvedScopeId() != null ? change.approvedScopeId() : input.scopeId(),
                  input.scopeName(),
                  input.usage(),
                  input.observedOn().atStartOfDay(SEOUL).toInstant(),
                  row.submittedAt(),
                  row.currentRevisionId(),
                  publicMedia.isEmpty()
                      ? null
                      : "/api/v1/media/" + publicMedia.getFirst() + "/public",
                  assertions),
              actor);
    } else if (change.approvedScopeId() != null || !publicMedia.isEmpty())
      throw invalid("승인할 때만 메뉴 연결과 사진 공개를 지정할 수 있습니다.");
    reports.review(
        id,
        row.currentRevisionId(),
        actor,
        change.decision(),
        change.reason(),
        row.version(),
        scope,
        publicMedia);
    reports.update(id, row.currentRevisionId(), change.decision());
    return view(reports.find(id, false).orElseThrow());
  }

  @Transactional(readOnly = true)
  public ReportView ownDetail(UUID owner, UUID id) {
    return view(owned(owner, id, false));
  }

  @Transactional(readOnly = true)
  public ReportView adminDetail(UUID id) {
    return view(reports.find(id, false).orElseThrow(ReportService::missing));
  }

  @Transactional(readOnly = true)
  public ReportPage list(UUID owner, String state, UUID cursor, int limit) {
    if (limit < 1
        || limit > 50
        || state != null && !STATES.contains(state)
        || cursor != null && !reports.cursorExists(cursor, owner, state))
      throw invalid("목록 조건과 다음 페이지를 확인해 주세요.");
    var rows = reports.list(owner, state, cursor, limit + 1);
    boolean more = rows.size() > limit;
    var items = more ? rows.subList(0, limit) : rows;
    return new ReportPage(items, more ? items.getLast().id() : null);
  }

  private void validate(ReportSubmission input) {
    if (!input.publicationConsent()
        || input.observedOn().isAfter(LocalDate.now(clock.withZone(SEOUL))))
      throw invalid("실제 관찰일과 공개 동의를 확인해 주세요.");
    if (!reports.publishedRestaurant(input.restaurantId()))
      throw new ApiException(HttpStatus.NOT_FOUND, "RESTAURANT_NOT_FOUND", "공개된 업소를 찾을 수 없습니다.");
    if (input.scopeId() != null
        && !reports.matchingScope(
            input.scopeId(), input.restaurantId(), input.scopeName(), input.usage()))
      throw invalid("선택한 메뉴·용도가 업소 정보와 일치하지 않습니다.");
    if (new HashSet<>(input.mediaIds()).size() != input.mediaIds().size())
      throw invalid("같은 사진을 중복 첨부할 수 없습니다.");
    var ingredients = new HashSet<UUID>();
    for (var claim : input.claims()) {
      if (!ingredients.add(claim.ingredientId()) || !reports.ingredientExists(claim.ingredientId()))
        throw invalid("식재료 선택과 중복 여부를 확인해 주세요.");
      value(claim);
      for (var component : claim.components())
        if (component.countryCode() != null && !reports.countryExists(component.countryCode()))
          throw invalid("카탈로그에 있는 국가를 선택해 주세요.");
    }
  }

  private OriginValue value(ReportSubmission.Claim claim) {
    try {
      return new OriginValue(
          OriginValue.Classification.valueOf(claim.classification()),
          claim.components().stream()
              .map(
                  part ->
                      new OriginValue.Component(
                          OriginValue.Classification.valueOf(part.kind()),
                          part.countryCode(),
                          part.ratio()))
              .toList());
    } catch (IllegalArgumentException exception) {
      throw invalid("원산지 분류·국가·혼합 비율을 확인해 주세요.");
    }
  }

  private ReportRepository.Row owned(UUID owner, UUID id, boolean lock) {
    var row = reports.find(id, lock).orElseThrow(ReportService::missing);
    if (!row.ownerId().equals(owner)) throw missing();
    return row;
  }

  private ReportView view(ReportRepository.Row row) {
    return new ReportView(
        row.id(),
        row.state(),
        row.version(),
        row.createdAt(),
        row.updatedAt(),
        json.readValue(row.body(), ReportSubmission.class),
        reports.reviews(row.id()));
  }

  private void limit(UUID owner) {
    if (!rate.allow(owner))
      throw new ApiException(
          HttpStatus.TOO_MANY_REQUESTS, "REPORT_RATE_LIMITED", "제보 변경 요청이 많습니다. 잠시 후 다시 시도해 주세요.");
  }

  private static void checkVersion(ReportRepository.Row row, long expected) {
    if (row.version() != expected) throw conflict("다른 변경이 반영되었습니다. 최신 내용을 확인해 주세요.");
  }

  private static void editable(ReportRepository.Row row) {
    if (!Set.of("PENDING", "NEEDS_MORE_INFO").contains(row.state()))
      throw conflict("검토 완료되거나 철회된 제보는 수정할 수 없습니다.");
  }

  private static ApiException invalid(String message) {
    return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REPORT", message);
  }

  private static ApiException conflict(String message) {
    return new ApiException(HttpStatus.CONFLICT, "REPORT_VERSION_CONFLICT", message);
  }

  private static ApiException missing() {
    return new ApiException(HttpStatus.NOT_FOUND, "REPORT_NOT_FOUND", "제보를 찾을 수 없습니다.");
  }
}
