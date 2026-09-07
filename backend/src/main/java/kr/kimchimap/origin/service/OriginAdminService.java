package kr.kimchimap.origin.service;

import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import kr.kimchimap.global.web.ApiException;
import kr.kimchimap.origin.dto.OriginAdministration;
import kr.kimchimap.origin.repository.OriginAdminRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OriginAdminService {
  private final OriginAdminRepository repository;
  private final OriginPublicationService publication;

  public OriginAdminService(
      OriginAdminRepository repository, OriginPublicationService publication) {
    this.repository = repository;
    this.publication = publication;
  }

  @Transactional(readOnly = true)
  public OriginAdministration.Groups list(String status, int limit, int offset) {
    if (limit < 1
        || limit > 50
        || offset < 0
        || offset > 10000
        || status != null && !List.of("CURRENT", "DISPUTED", "UNAVAILABLE").contains(status))
      throw invalid("목록 상태·조회 범위를 확인해 주세요.");
    var rows = repository.groups(status, limit + 1, offset);
    boolean more = rows.size() > limit;
    return new OriginAdministration.Groups(
        more ? rows.subList(0, limit) : rows,
        more && offset + limit <= 10000 ? offset + limit : null,
        more && offset + limit > 10000);
  }

  @Transactional(
      readOnly = true,
      isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
  public OriginAdministration.Detail detail(UUID scope, UUID ingredient) {
    var group = repository.group(scope, ingredient, false).orElseThrow(OriginAdminService::missing);
    var records = repository.records(scope, ingredient);
    checkSize(records.size());
    return new OriginAdministration.Detail(group, records, repository.audits(scope, ingredient));
  }

  @Transactional
  public OriginAdministration.Detail correct(UUID actor, OriginAdministration.Correction input) {
    var group =
        repository
            .group(input.scopeId(), input.ingredientId(), true)
            .orElseThrow(OriginAdminService::missing);
    if (group.version() != input.expectedVersion())
      throw new ApiException(
          HttpStatus.CONFLICT, "ORIGIN_VERSION_CONFLICT", "원산지 정보가 변경되었습니다. 최신 근거를 다시 검토해 주세요.");
    var records = repository.records(input.scopeId(), input.ingredientId());
    checkSize(records.size());
    var selected = new HashSet<>(input.withdrawRecordIds());
    var eligible =
        records.stream()
            .filter(row -> !row.withdrawn())
            .map(OriginAdministration.Record::id)
            .toList();
    if (selected.size() != input.withdrawRecordIds().size() || !eligible.containsAll(selected))
      throw invalid("같은 메뉴·용도·식재료의 철회되지 않은 기록만 선택해 주세요.");
    for (UUID id : selected) repository.withdraw(id, input.reason(), actor);
    publication.republish(input.scopeId(), input.ingredientId(), actor);
    repository.audit(input, actor);
    return detail(input.scopeId(), input.ingredientId());
  }

  private static void checkSize(int size) {
    if (size > 2000)
      throw new ApiException(
          HttpStatus.UNPROCESSABLE_CONTENT, "ORIGIN_HISTORY_LIMIT", "원산지 이력이 많아 검토 범위를 초과했습니다.");
  }

  private static ApiException missing() {
    return new ApiException(
        HttpStatus.NOT_FOUND, "ORIGIN_GROUP_NOT_FOUND", "원산지 판정 정보를 찾을 수 없습니다.");
  }

  private static ApiException invalid(String message) {
    return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ORIGIN_CORRECTION", message);
  }
}
