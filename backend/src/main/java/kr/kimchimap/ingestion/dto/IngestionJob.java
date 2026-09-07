package kr.kimchimap.ingestion.dto;

import java.time.Instant;
import java.util.UUID;

public record IngestionJob(
    UUID id,
    UUID sourceId,
    String mode,
    String status,
    Instant sinceAt,
    Instant untilAt,
    int pageSize,
    int nextPage,
    int maxPages,
    int pagesProcessed,
    Long totalCount,
    long readCount,
    long changedCount,
    long quarantinedCount,
    boolean fullListingCompleted,
    UUID leaseOwner,
    long fencingToken,
    Instant leaseUntil,
    Instant nextAttemptAt,
    int retryCount,
    String errorCode,
    Instant requestedAt,
    Instant updatedAt) {}
