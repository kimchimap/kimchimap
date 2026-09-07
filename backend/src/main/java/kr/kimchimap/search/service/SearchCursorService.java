package kr.kimchimap.search.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.TreeSet;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import kr.kimchimap.global.web.ApiException;
import kr.kimchimap.search.dto.SearchRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
public class SearchCursorService {
  public record Position(double distanceMeters, UUID id, Instant asOf) {}

  private record Payload(
      int version, String fingerprint, double distanceMeters, UUID id, Instant asOf) {}

  private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
  private static final Base64.Decoder DECODER = Base64.getUrlDecoder();
  private final byte[] key;
  private final ObjectMapper mapper;
  private final Clock clock;

  public SearchCursorService(
      ObjectMapper mapper,
      Clock clock,
      Environment environment,
      @Value("${app.search.cursor-secret:}") String secret) {
    this.mapper = mapper;
    this.clock = clock;
    if (secret.isBlank()) {
      if (environment.matchesProfiles("prod"))
        throw new IllegalStateException("운영 검색 cursor 서명키가 필요합니다.");
      key = new byte[32];
      new SecureRandom().nextBytes(key);
    } else {
      if (secret.getBytes(StandardCharsets.UTF_8).length < 32)
        throw new IllegalStateException("검색 cursor 서명키는 32바이트 이상이어야 합니다.");
      key = secret.getBytes(StandardCharsets.UTF_8);
    }
  }

  public String encode(SearchRequest request, Position position) {
    var payload =
        new Payload(
            1, fingerprint(request), position.distanceMeters(), position.id(), position.asOf());
    var body = ENCODER.encodeToString(mapper.writeValueAsBytes(payload));
    return body + "." + ENCODER.encodeToString(sign(body));
  }

  public Position decode(SearchRequest request) {
    if (request.cursor() == null) return null;
    try {
      var parts = request.cursor().split("\\.", -1);
      if (parts.length != 2 || !MessageDigest.isEqual(sign(parts[0]), DECODER.decode(parts[1])))
        throw invalid();
      var value = mapper.readValue(DECODER.decode(parts[0]), Payload.class);
      if (value.version() != 1
          || !fingerprint(request).equals(value.fingerprint())
          || value.id() == null
          || value.asOf() == null
          || value.asOf().isAfter(clock.instant())
          || value.asOf().isBefore(clock.instant().minus(Duration.ofMinutes(10)))
          || !Double.isFinite(value.distanceMeters())
          || value.distanceMeters() < 0) throw invalid();
      return new Position(value.distanceMeters(), value.id(), value.asOf());
    } catch (RuntimeException exception) {
      throw invalid();
    }
  }

  private String fingerprint(SearchRequest request) {
    // 집합의 순서는 요청마다 달라도 같은 검색 조건으로 취급한다.
    var canonical =
        java.util.Arrays.asList(
            request.center(),
            request.bounds(),
            request.radiusMeters(),
            request.groups().stream()
                .map(
                    g ->
                        java.util.Arrays.asList(
                            g.usage(),
                            g.ingredients().stream()
                                .map(
                                    i ->
                                        java.util.Arrays.asList(
                                            i.ingredientId(),
                                            i.mode(),
                                            new TreeSet<>(i.countries()),
                                            i.includeMixed()))
                                .toList()))
                .toList(),
            request.maxAgeDays(),
            new TreeSet<>(request.evidenceKinds()),
            request.limit());
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsBytes(canonical)));
    } catch (java.security.NoSuchAlgorithmException exception) {
      throw new IllegalStateException(exception);
    }
  }

  private byte[] sign(String value) {
    try {
      var mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(key, "HmacSHA256"));
      return mac.doFinal(value.getBytes(StandardCharsets.US_ASCII));
    } catch (java.security.GeneralSecurityException exception) {
      throw new IllegalStateException(exception);
    }
  }

  private ApiException invalid() {
    return new ApiException(
        HttpStatus.BAD_REQUEST, "INVALID_CURSOR", "페이지 정보가 만료되었거나 검색 조건이 변경되었습니다. 다시 검색해 주세요.");
  }
}
