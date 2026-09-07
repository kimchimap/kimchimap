package kr.kimchimap.auth.dto;

import java.util.UUID;

public record AuthenticatedMember(UUID memberId, UUID sessionId, String role)
    implements java.security.Principal {
  @Override
  public String getName() {
    return memberId.toString();
  }
}
