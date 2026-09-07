package kr.kimchimap.auth.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import kr.kimchimap.auth.dto.ActiveSession;
import kr.kimchimap.auth.dto.AuthenticatedMember;
import kr.kimchimap.auth.repository.SessionRepository;
import kr.kimchimap.global.web.ApiException;
import kr.kimchimap.member.dto.MemberIdentity;
import kr.kimchimap.member.service.MemberService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class SessionService {
  public record Issued(ActiveSession session, String refreshToken) {
    @Override
    public String toString() {
      return "Issued[session=" + session.id() + ", refreshToken=비공개]";
    }
  }

  private final SessionRepository repository;
  private final MemberService members;
  private final Clock clock;
  private final Duration lifetime;

  public SessionService(
      SessionRepository repository,
      MemberService members,
      Clock clock,
      @Value("${app.auth.session-lifetime:14d}") Duration lifetime) {
    if (lifetime.isNegative() || lifetime.isZero() || lifetime.compareTo(Duration.ofDays(14)) > 0)
      throw new IllegalArgumentException("세션 수명은 14일 이내여야 합니다.");
    this.repository = repository;
    this.members = members;
    this.clock = clock;
    this.lifetime = lifetime;
  }

  public Issued create(MemberIdentity member) {
    var current = members.requireActive(member.id());
    UUID id = UUID.randomUUID();
    String token = TokenSecrets.generate();
    String result =
        repository.create(
            id, current.id(), current.securityVersion(), TokenSecrets.hash(token), lifetime);
    if ("LIMIT".equals(result))
      throw new ApiException(
          HttpStatus.TOO_MANY_REQUESTS, "SESSION_LIMIT", "로그인된 기기에서 세션을 정리한 뒤 다시 시도해 주세요.");
    if (result == null || !result.matches("[0-9]+"))
      throw new IllegalStateException("세션을 생성하지 못했습니다.");
    return new Issued(
        new ActiveSession(
            id,
            current.id(),
            current.securityVersion(),
            Instant.ofEpochMilli(Long.parseLong(result))),
        token);
  }

  public Issued rotate(String token) {
    String hash = validatedHash(token);
    UUID id = repository.lookup(hash).orElseThrow(SessionService::invalid);
    var session = requireActive(id);
    String next = TokenSecrets.generate();
    String result = repository.rotate(id, hash, TokenSecrets.hash(next));
    if (!"OK".equals(result)) throw invalid();
    return new Issued(session, next);
  }

  public ActiveSession requireActive(UUID id) {
    var session = repository.active(id).orElseThrow(SessionService::invalid);
    if (!session.expiresAt().isAfter(clock.instant())) throw invalid();
    var member = members.requireActive(session.memberId());
    if (session.securityVersion() != member.securityVersion()) throw invalid();
    return session;
  }

  public AuthenticatedMember authenticate(UUID id, UUID subject, long version) {
    var session = repository.active(id).orElseThrow(SessionService::invalid);
    var member = members.requireActive(session.memberId());
    if (!session.expiresAt().isAfter(clock.instant())
        || !session.memberId().equals(subject)
        || version != session.securityVersion()
        || version != member.securityVersion()) throw invalid();
    return new AuthenticatedMember(member.id(), session.id(), member.role());
  }

  public void withdraw(UUID member) {
    var current = members.requireActive(member);
    repository.revokeAll(member);
    members.changeSecurity(member, current.role(), "WITHDRAWN", "본인 회원 탈퇴", member.toString());
  }

  public void revoke(UUID id) {
    repository.revoke(id);
  }

  public void revokeAll(UUID member) {
    repository.revokeAll(member);
  }

  public boolean allowRefresh(String clientAddress) {
    return repository.allowRefresh(TokenSecrets.hash(clientAddress));
  }

  public void logout(String token) {
    if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) return;
    repository.lookup(validatedHash(token)).ifPresent(repository::revoke);
  }

  private String validatedHash(String token) {
    if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) throw invalid();
    return TokenSecrets.hash(token);
  }

  private static ApiException invalid() {
    return new ApiException(
        HttpStatus.UNAUTHORIZED, "SESSION_INVALID", "로그인이 만료되었습니다. 다시 로그인해 주세요.");
  }
}
