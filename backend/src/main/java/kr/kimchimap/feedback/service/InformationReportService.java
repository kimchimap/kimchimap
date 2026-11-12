package kr.kimchimap.feedback.service;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import kr.kimchimap.auth.service.TokenSecrets;
import kr.kimchimap.feedback.dto.InformationReports;
import kr.kimchimap.feedback.repository.InformationReportRepository;
import kr.kimchimap.feedback.repository.InformationReportRepository.Row;
import kr.kimchimap.global.web.ApiException;
import kr.kimchimap.media.service.MediaService;
import kr.kimchimap.origin.service.OriginAdminService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class InformationReportService {
  private final InformationReportRepository reports;
  private final MediaService media;
  private final OriginAdminService origins;
  private final ObjectMapper json;

  public InformationReportService(
      InformationReportRepository reports,
      MediaService media,
      OriginAdminService origins,
      ObjectMapper json) {
    this.reports = reports;
    this.media = media;
    this.origins = origins;
    this.json = json;
  }

  @Transactional
  public InformationReports.View create(UUID owner, UUID key, InformationReports.Submission input) {
    reports.lockOwner(owner);
    String hash = TokenSecrets.hash(json.writeValueAsString(input));
    var previous = reports.repeated(owner, key);
    if (previous.isPresent()) {
      if (!previous.get().requestHash().equals(hash))
        throw error(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT", "같은 요청 키에 다른 신고 내용이 전달되었습니다.");
      return view(previous.get());
    }
    if (!reports.published(input.restaurantId()))
      throw error(HttpStatus.NOT_FOUND, "RESTAURANT_NOT_FOUND", "공개된 업소를 찾을 수 없습니다.");
    if (new HashSet<>(input.mediaIds()).size() != input.mediaIds().size())
      throw error(HttpStatus.BAD_REQUEST, "INVALID_INFORMATION_REPORT", "첨부 사진은 중복할 수 없습니다.");
    if (reports.recentCount(owner) >= 20)
      throw error(
          HttpStatus.TOO_MANY_REQUESTS,
          "INFORMATION_REPORT_RATE_LIMITED",
          "신고 요청이 많습니다. 잠시 후 다시 시도해 주세요.");
    if (!input.mediaIds().isEmpty()) media.attach(owner, input.mediaIds());
    return detail(owner, reports.create(owner, key, hash, input));
  }

  @Transactional(readOnly = true)
  public InformationReports.View detail(UUID owner, UUID id) {
    var row = reports.find(id, false).orElseThrow(InformationReportService::missing);
    if (owner != null && !owner.equals(row.ownerId())) throw missing();
    return view(row);
  }

  @Transactional(readOnly = true)
  public InformationReports.Page list(UUID owner, String state, UUID cursor, int limit) {
    if (limit < 1
        || limit > 50
        || state != null && !Set.of("PENDING", "RESOLVED", "DISMISSED").contains(state)
        || cursor != null && !reports.cursorExists(owner, cursor, state))
      throw error(HttpStatus.BAD_REQUEST, "INVALID_PAGE", "신고 목록 조건을 확인해 주세요.");
    var rows = reports.list(owner, state, cursor, limit + 1);
    boolean more = rows.size() > limit;
    var items =
        (more ? rows.subList(0, limit) : rows)
            .stream()
                .map(
                    row ->
                        new InformationReports.Item(
                            row.id(),
                            row.restaurantId(),
                            row.restaurantName(),
                            row.category(),
                            row.state(),
                            row.createdAt()))
                .toList();
    return new InformationReports.Page(items, more ? items.getLast().id() : null);
  }

  @Transactional
  public InformationReports.View resolve(UUID actor, UUID id, InformationReports.Resolution input) {
    var row = reports.find(id, true).orElseThrow(InformationReportService::missing);
    if (!row.state().equals("PENDING") || row.version() != input.expectedVersion())
      throw error(
          HttpStatus.CONFLICT,
          "INFORMATION_REPORT_VERSION_CONFLICT",
          "이미 처리되었거나 변경된 신고입니다. 최신 내용을 확인해 주세요.");
    if (input.correction() != null) {
      if (!input.decision().equals("RESOLVED") || !row.category().equals("ORIGIN_CHANGED"))
        throw error(
            HttpStatus.BAD_REQUEST,
            "INVALID_INFORMATION_REPORT_RESOLUTION",
            "원산지 변경 신고를 처리할 때만 근거를 정정할 수 있습니다.");
      var target = origins.detail(input.correction().scopeId(), input.correction().ingredientId());
      if (!target.group().restaurantId().equals(row.restaurantId()))
        throw error(
            HttpStatus.BAD_REQUEST,
            "INVALID_INFORMATION_REPORT_RESOLUTION",
            "신고한 업소의 원산지만 정정할 수 있습니다.");
      origins.correct(actor, input.correction());
    }
    reports.resolve(id, actor, input);
    return detail(null, id);
  }

  private InformationReports.View view(Row row) {
    return new InformationReports.View(
        row.id(),
        row.restaurantId(),
        row.restaurantName(),
        row.category(),
        row.description(),
        row.state(),
        row.version(),
        row.createdAt(),
        reports.media(row.id()),
        row.resolutionReason(),
        row.resolvedAt());
  }

  private static ApiException error(HttpStatus status, String code, String message) {
    return new ApiException(status, code, message);
  }

  private static ApiException missing() {
    return error(HttpStatus.NOT_FOUND, "INFORMATION_REPORT_NOT_FOUND", "신고를 찾을 수 없습니다.");
  }
}
