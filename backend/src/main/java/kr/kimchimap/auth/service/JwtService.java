package kr.kimchimap.auth.service;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import kr.kimchimap.auth.dto.ActiveSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
  private final NimbusJwtEncoder encoder;
  private final NimbusJwtDecoder decoder;
  private final Clock clock;
  private final String issuer;
  private final String audience;
  private final String keyId;
  private final Duration lifetime;
  private final Set<String> trustedKeyIds;

  public JwtService(
      Environment environment,
      Clock clock,
      @Value("${app.auth.jwt-issuer:urn:kimchimap:auth}") String issuer,
      @Value("${app.auth.jwt-audience:kimchimap-api}") String audience,
      @Value("${app.auth.access-lifetime:10m}") Duration lifetime)
      throws Exception {
    if (issuer.isBlank()
        || audience.isBlank()
        || lifetime.isZero()
        || lifetime.isNegative()
        || lifetime.compareTo(Duration.ofMinutes(10)) > 0)
      throw new IllegalArgumentException("JWT 발급 정책을 확인해 주세요.");
    this.clock = clock;
    this.issuer = issuer;
    this.audience = audience;
    this.lifetime = lifetime;
    RSAKey signing = loadSigningKey(environment);
    keyId = signing.getKeyID();
    var publicKeys = new ArrayList<JWK>();
    publicKeys.add(signing.toPublicJWK());
    String verification = environment.getProperty("JWT_VERIFICATION_KEYS_PATH", "");
    if (!verification.isBlank()) {
      for (JWK key : JWKSet.parse(Files.readString(Path.of(verification))).getKeys()) {
        if (!(key instanceof RSAKey rsa)
            || key.isPrivate()
            || key.getKeyID() == null
            || rsa.size() < 2048
            || key.getAlgorithm() != null && !JWSAlgorithm.RS256.equals(key.getAlgorithm()))
          throw new IllegalArgumentException("검증 키 파일에는 식별자가 있는 RSA 공개키만 허용합니다.");
        publicKeys.add(key);
      }
    }
    trustedKeyIds = publicKeys.stream().map(JWK::getKeyID).collect(Collectors.toUnmodifiableSet());
    if (trustedKeyIds.size() != publicKeys.size())
      throw new IllegalArgumentException("JWT 키 식별자가 중복됩니다.");
    encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(signing)));
    var processor = new DefaultJWTProcessor<SecurityContext>();
    // 시간·필수 클레임은 아래 Spring validator에서 같은 Clock과 정책으로 검증한다.
    processor.setJWTClaimsSetVerifier((claims, context) -> {});
    processor.setJWSKeySelector(
        new JWSVerificationKeySelector<>(
            JWSAlgorithm.RS256, new ImmutableJWKSet<>(new JWKSet(publicKeys))));
    decoder = new NimbusJwtDecoder(processor);
    var timestamps = new JwtTimestampValidator(Duration.ofSeconds(30));
    timestamps.setClock(clock);
    decoder.setJwtValidator(
        new DelegatingOAuth2TokenValidator<>(
            timestamps, new JwtIssuerValidator(issuer), this::claims));
  }

  public JwtDecoder decoder() {
    return decoder;
  }

  public String issue(ActiveSession session) {
    Instant now = clock.instant();
    Instant expiry =
        now.plus(lifetime).isBefore(session.expiresAt()) ? now.plus(lifetime) : session.expiresAt();
    var claims =
        JwtClaimsSet.builder()
            .issuer(issuer)
            .audience(java.util.List.of(audience))
            .subject(session.memberId().toString())
            .issuedAt(now)
            .expiresAt(expiry)
            .id(UUID.randomUUID().toString())
            .claim("sid", session.id().toString())
            .claim("ver", session.securityVersion())
            .build();
    return encoder
        .encode(
            JwtEncoderParameters.from(
                JwsHeader.with(SignatureAlgorithm.RS256).keyId(keyId).type("JWT").build(), claims))
        .getTokenValue();
  }

  private OAuth2TokenValidatorResult claims(Jwt jwt) {
    try {
      if (!jwt.getAudience().equals(java.util.List.of(audience))
          || !trustedKeyIds.contains(jwt.getHeaders().get("kid"))
          || !"JWT".equals(jwt.getHeaders().get("typ"))
          || jwt.getIssuedAt() == null
          || jwt.getExpiresAt() == null
          || jwt.getIssuedAt().isAfter(clock.instant().plusSeconds(30))
          || !jwt.getExpiresAt().isAfter(jwt.getIssuedAt())
          || Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())
                  .compareTo(Duration.ofMinutes(10))
              > 0
          || !(jwt.getClaim("ver") instanceof Number version)
          || version.longValue() < 0) return invalid();
      UUID.fromString(jwt.getSubject());
      UUID.fromString(jwt.getId());
      UUID.fromString(jwt.getClaimAsString("sid"));
      return OAuth2TokenValidatorResult.success();
    } catch (RuntimeException exception) {
      return invalid();
    }
  }

  private OAuth2TokenValidatorResult invalid() {
    return OAuth2TokenValidatorResult.failure(
        new OAuth2Error("invalid_token", "서비스 토큰을 확인할 수 없습니다.", null));
  }

  private RSAKey loadSigningKey(Environment environment) throws Exception {
    String path = environment.getProperty("JWT_PRIVATE_KEY_PATH", "");
    String kid = environment.getProperty("JWT_KEY_ID", "");
    if (path.isBlank()) {
      if (environment.matchesProfiles("prod") || !environment.matchesProfiles("local", "test"))
        throw new IllegalStateException("외부 JWT 서명키가 필요합니다.");
      var generator = KeyPairGenerator.getInstance("RSA");
      generator.initialize(3072);
      var pair = generator.generateKeyPair();
      return new RSAKey.Builder((RSAPublicKey) pair.getPublic())
          .privateKey(pair.getPrivate())
          .keyID("local-" + UUID.randomUUID())
          .build();
    }
    if (kid.isBlank() || kid.length() > 100)
      throw new IllegalArgumentException("JWT 키 식별자가 필요합니다.");
    JWK parsed = JWK.parse(Files.readString(Path.of(path)));
    if (!(parsed instanceof RSAKey key)
        || !key.isPrivate()
        || key.size() < 2048
        || key.getKeyID() != null && !kid.equals(key.getKeyID())
        || key.getAlgorithm() != null && !JWSAlgorithm.RS256.equals(key.getAlgorithm())
        || key.getKeyUse() != null
            && !com.nimbusds.jose.jwk.KeyUse.SIGNATURE.equals(key.getKeyUse()))
      throw new IllegalArgumentException("서명용 RSA 개인 JWK와 키 식별자를 확인해 주세요.");
    return new RSAKey.Builder(key).keyID(kid).build();
  }
}
