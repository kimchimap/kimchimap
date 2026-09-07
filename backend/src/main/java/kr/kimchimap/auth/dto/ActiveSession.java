package kr.kimchimap.auth.dto;

import java.time.Instant;
import java.util.UUID;

public record ActiveSession(UUID id, UUID memberId, long securityVersion, Instant expiresAt) {}
