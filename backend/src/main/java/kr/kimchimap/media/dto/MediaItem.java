package kr.kimchimap.media.dto;

import java.time.Instant;
import java.util.UUID;

public record MediaItem(
    UUID id, String contentType, long size, int width, int height, Instant createdAt) {}
