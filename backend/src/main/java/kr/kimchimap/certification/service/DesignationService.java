package kr.kimchimap.certification.service;

import java.util.List;
import java.util.UUID;
import kr.kimchimap.certification.dto.DesignationAdministration;
import kr.kimchimap.certification.repository.DesignationRepository;
import kr.kimchimap.global.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class DesignationService {
  private final DesignationRepository repository;
  private final ObjectMapper json;

  public DesignationService(DesignationRepository repository, ObjectMapper json) {
    this.repository = repository;
    this.json = json;
  }

  @Transactional(readOnly = true)
  public List<DesignationAdministration.Source> sources() {
    return repository.sources();
  }

  @Transactional(readOnly = true)
  public List<DesignationAdministration.Item> recent(UUID restaurant) {
    return repository.recent(restaurant);
  }

  @Transactional(readOnly = true)
  public DesignationAdministration.View detail(UUID id) {
    var row = repository.find(id, false).orElseThrow(DesignationService::missing);
    return new DesignationAdministration.View(
        id, row.version(), row.reviewedAt(), row.input(), repository.history(id));
  }

  @Transactional
  public DesignationAdministration.View create(
      UUID actor, DesignationAdministration.Create request) {
    validate(request.designation());
    UUID id = UUID.randomUUID();
    try {
      repository.create(id, request.designation());
    } catch (org.springframework.dao.DuplicateKeyException exception) {
      throw new ApiException(
          HttpStatus.CONFLICT, "DESIGNATION_EXISTS", "같은 출처의 지정 식별자가 이미 등록되어 있습니다.");
    }
    repository.revision(
        id, 0, json.writeValueAsString(request.designation()), actor, request.reason());
    return detail(id);
  }

  @Transactional
  public DesignationAdministration.View update(
      UUID actor, UUID id, DesignationAdministration.Update request) {
    var row = repository.find(id, true).orElseThrow(DesignationService::missing);
    if (row.version() != request.expectedVersion())
      throw new ApiException(
          HttpStatus.CONFLICT, "DESIGNATION_VERSION_CONFLICT", "지정 정보가 변경되었습니다. 최신 이력을 확인해 주세요.");
    var input = request.designation();
    if (!row.restaurantId().equals(input.restaurantId())
        || !row.sourceId().equals(input.sourceId())
        || !row.externalId().equals(input.externalId())) throw invalid("업소·출처·외부 식별자는 변경할 수 없습니다.");
    validate(input);
    repository.update(id, input);
    repository.revision(
        id, row.version() + 1, json.writeValueAsString(input), actor, request.reason());
    return detail(id);
  }

  private void validate(DesignationAdministration.Input input) {
    if (!repository.lockAllowedSource(input.sourceId()))
      throw new ApiException(
          HttpStatus.CONFLICT,
          "DESIGNATION_PERMISSION_REQUIRED",
          "지정 정보의 수집·저장·재게시 허가가 확인된 소스가 필요합니다.");
    if (!repository.restaurantExists(input.restaurantId())) throw invalid("공개 업소를 선택해 주세요.");
    if (input.designatedOn() != null
        && (input.expiresOn() != null && input.expiresOn().isBefore(input.designatedOn())
            || input.cancelledOn() != null && input.cancelledOn().isBefore(input.designatedOn())))
      throw invalid("지정·만료·취소 날짜의 순서를 확인해 주세요.");
  }

  private static ApiException missing() {
    return new ApiException(HttpStatus.NOT_FOUND, "DESIGNATION_NOT_FOUND", "지정 정보를 찾을 수 없습니다.");
  }

  private static ApiException invalid(String message) {
    return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DESIGNATION", message);
  }
}
