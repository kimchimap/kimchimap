package kr.kimchimap.member.config;

import java.util.UUID;
import kr.kimchimap.member.service.MemberService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.member.command", havingValue = "admin")
public class MemberAdminCommand implements ApplicationRunner {
  private final MemberService members;
  private final ConfigurableApplicationContext context;
  private final String id;
  private final String role;
  private final String reason;
  private final boolean apply;

  public MemberAdminCommand(
      MemberService members,
      ConfigurableApplicationContext context,
      @Value("${app.member.id:}") String id,
      @Value("${app.member.role:ADMIN}") String role,
      @Value("${app.member.reason:}") String reason,
      @Value("${app.member.apply:false}") boolean apply) {
    this.members = members;
    this.context = context;
    this.id = id;
    this.role = role;
    this.reason = reason;
    this.apply = apply;
  }

  @Override
  public void run(ApplicationArguments args) {
    try {
      UUID memberId = UUID.fromString(id);
      if (!java.util.Set.of("ADMIN", "USER").contains(role)
          || reason.isBlank()
          || reason.length() > 2000)
        throw new IllegalArgumentException("대상 권한과 2천 자 이하 변경 사유가 필요합니다.");
      var member = members.requireActive(memberId);
      System.out.printf("회원=%s 현재권한=%s 변경권한=%s 적용=%s%n", member.id(), member.role(), role, apply);
      if (apply) {
        members.changeSecurity(
            memberId,
            role,
            "ACTIVE",
            reason,
            "server-command:" + System.getProperty("user.name", "unknown"));
        System.out.printf("권한 변경 확인=%s%n", members.requireActive(memberId).role());
      } else System.out.println("변경 계획만 확인했습니다. 실제 적용에는 명시적 apply 설정이 필요합니다.");
    } finally {
      context.close();
    }
  }
}
