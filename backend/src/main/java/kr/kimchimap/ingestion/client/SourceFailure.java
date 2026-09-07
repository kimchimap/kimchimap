package kr.kimchimap.ingestion.client;

import java.time.Duration;

public class SourceFailure extends RuntimeException {
  private final String code;
  private final boolean retryable;
  private final Duration retryAfter;

  public SourceFailure(String code, boolean retryable, Duration retryAfter) {
    super("외부 수집 실패: " + code);
    this.code = code;
    this.retryable = retryable;
    this.retryAfter = retryAfter;
  }

  public String code() {
    return code;
  }

  public boolean retryable() {
    return retryable;
  }

  public Duration retryAfter() {
    return retryAfter;
  }
}
