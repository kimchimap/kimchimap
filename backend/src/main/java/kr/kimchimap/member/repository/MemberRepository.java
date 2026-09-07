package kr.kimchimap.member.repository;

import java.util.Optional;
import java.util.UUID;
import kr.kimchimap.member.dto.MemberIdentity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class MemberRepository {
  private final JdbcClient jdbc;

  public MemberRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  public MemberIdentity findOrCreate(String subject) {
    return jdbc.sql(
            """
        INSERT INTO app.member(id, provider, provider_subject) VALUES (:id, 'KAKAO', :subject)
        ON CONFLICT(provider,provider_subject) DO UPDATE SET provider=EXCLUDED.provider
        RETURNING id,role,status,security_version
        """)
        .param("id", UUID.randomUUID())
        .param("subject", subject)
        .query(MemberIdentity.class)
        .single();
  }

  public Optional<MemberIdentity> find(UUID id) {
    return jdbc.sql("SELECT id,role,status,security_version FROM app.member WHERE id=:id")
        .param("id", id)
        .query(MemberIdentity.class)
        .optional();
  }

  public void changeSecurity(UUID id, String role, String status, String reason, String actor) {
    int count =
        jdbc.sql(
                """
        UPDATE app.member SET role=:role,status=:status,security_version=security_version+1,
          provider_subject=CASE WHEN :status='WITHDRAWN' THEN NULL ELSE provider_subject END
        WHERE id=:id AND status<>'WITHDRAWN'
        """)
            .param("id", id)
            .param("role", role)
            .param("status", status)
            .update();
    if (count != 1) throw new IllegalArgumentException("변경할 회원을 찾을 수 없습니다.");
    jdbc.sql(
            "INSERT INTO app.member_security_history(member_id,action,reason,actor_reference) VALUES (:id,:action,:reason,:actor)")
        .param("id", id)
        .param("action", status + ":" + role)
        .param("reason", reason)
        .param("actor", actor)
        .update();
  }
}
