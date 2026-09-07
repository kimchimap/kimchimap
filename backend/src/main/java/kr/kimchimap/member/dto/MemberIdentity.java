package kr.kimchimap.member.dto;

import java.util.UUID;

public record MemberIdentity(UUID id, String role, String status, long securityVersion) {}
