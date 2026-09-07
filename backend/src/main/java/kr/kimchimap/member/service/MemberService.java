package kr.kimchimap.member.service;

import java.util.Set;
import java.util.UUID;
import kr.kimchimap.global.web.ApiException;
import kr.kimchimap.member.dto.MemberIdentity;
import kr.kimchimap.member.repository.MemberRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MemberService {
  private final MemberRepository repository;

  public MemberService(MemberRepository repository) {
    this.repository = repository;
  }

  @Transactional
  public MemberIdentity loginWithVerifiedKakaoSubject(String subject) {
    if (subject == null || subject.isBlank() || subject.length() > 200)
      throw new IllegalArgumentException("유효한 공급자 식별자가 필요합니다.");
    return active(repository.findOrCreate(subject));
  }

  @Transactional(readOnly = true)
  public MemberIdentity requireActive(UUID id) {
    return active(repository.find(id).orElseThrow(MemberService::unavailable));
  }

  @Transactional
  public void changeSecurity(UUID id, String role, String status, String reason, String actor) {
    if (!Set.of("USER", "ADMIN").contains(role)
        || !Set.of("ACTIVE", "SUSPENDED", "WITHDRAWN").contains(status)
        || reason == null
        || reason.isBlank()
        || actor == null
        || actor.isBlank()) throw new IllegalArgumentException("권한 변경의 상태·실행자·사유가 필요합니다.");
    repository.changeSecurity(id, role, status, reason, actor);
  }

  private MemberIdentity active(MemberIdentity member) {
    if (!member.status().equals("ACTIVE")) throw unavailable();
    return member;
  }

  private static ApiException unavailable() {
    return new ApiException(
        HttpStatus.UNAUTHORIZED, "MEMBER_UNAVAILABLE", "이 계정으로 서비스를 이용할 수 없습니다.");
  }
}
