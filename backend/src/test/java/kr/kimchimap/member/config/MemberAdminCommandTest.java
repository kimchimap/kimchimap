package kr.kimchimap.member.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import java.util.UUID;
import kr.kimchimap.member.dto.MemberIdentity;
import kr.kimchimap.member.service.MemberService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.context.ConfigurableApplicationContext;

class MemberAdminCommandTest {
  @Test
  void dryRunDoesNotAssignAdminAndExplicitApplyAuditsChange() {
    var members = mock(MemberService.class);
    var context = mock(ConfigurableApplicationContext.class);
    UUID id = UUID.randomUUID();
    when(members.requireActive(id)).thenReturn(new MemberIdentity(id, "USER", "ACTIVE", 1));
    new MemberAdminCommand(members, context, id.toString(), "ADMIN", "테스트 권한 부여", false)
        .run(new DefaultApplicationArguments());
    verify(members, never()).changeSecurity(any(), any(), any(), any(), any());
    new MemberAdminCommand(members, context, id.toString(), "ADMIN", "테스트 권한 부여", true)
        .run(new DefaultApplicationArguments());
    verify(members)
        .changeSecurity(
            eq(id), eq("ADMIN"), eq("ACTIVE"), eq("테스트 권한 부여"), startsWith("server-command:"));
    verify(context, times(2)).close();
  }

  @Test
  void refusesUnsupportedRoleOrMissingReason() {
    var members = mock(MemberService.class);
    var context = mock(ConfigurableApplicationContext.class);
    assertThatThrownBy(
            () ->
                new MemberAdminCommand(
                        members, context, UUID.randomUUID().toString(), "OWNER", "", true)
                    .run(new DefaultApplicationArguments()))
        .isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(members);
    verify(context).close();
  }
}
