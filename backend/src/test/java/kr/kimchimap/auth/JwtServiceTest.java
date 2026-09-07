package kr.kimchimap.auth;

import static org.assertj.core.api.Assertions.*;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;
import kr.kimchimap.auth.dto.ActiveSession;
import kr.kimchimap.auth.service.JwtService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.oauth2.jwt.JwtException;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JwtServiceTest {
  private final Instant now = Instant.parse("2026-09-07T00:00:00Z");
  private JwtService service;
  private RSAKey signing;

  @BeforeAll
  void initialize(@TempDir Path dir) throws Exception {
    var generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    var pair = generator.generateKeyPair();
    signing =
        new RSAKey.Builder((RSAPublicKey) pair.getPublic())
            .privateKey((java.security.interfaces.RSAPrivateCrtKey) pair.getPrivate())
            .keyID("test-kid")
            .build();
    Path file = dir.resolve("test-private.jwk");
    Files.writeString(file, signing.toJSONString());
    var environment =
        new MockEnvironment()
            .withProperty("JWT_PRIVATE_KEY_PATH", file.toString())
            .withProperty("JWT_KEY_ID", "test-kid");
    environment.setActiveProfiles("test");
    service =
        new JwtService(
            environment,
            Clock.fixed(now, ZoneOffset.UTC),
            "urn:kimchimap:auth",
            "kimchimap-api",
            Duration.ofMinutes(10));
  }

  @Test
  void validTokenHasOnlyServiceClaimsAndRespectsAbsoluteSessionExpiry() {
    UUID member = UUID.randomUUID(), sid = UUID.randomUUID();
    var token =
        service
            .decoder()
            .decode(service.issue(new ActiveSession(sid, member, 0, now.plusSeconds(60))));
    assertThat(token.getSubject()).isEqualTo(member.toString());
    assertThat(token.getExpiresAt()).isEqualTo(now.plusSeconds(60));
    assertThat(token.getClaims().keySet())
        .containsExactlyInAnyOrder("iss", "aud", "sub", "iat", "exp", "jti", "sid", "ver");
  }

  @Test
  void alteredUnsignedWrongIssuerAudienceAndExpiredTokensAreRejected() throws Exception {
    var valid = base();
    String signed = sign(valid.build(), "test-kid");
    String[] parts = signed.split("\\.");
    String altered =
        parts[0]
            + "."
            + Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(
                    "{\"sub\":\"altered\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8))
            + "."
            + parts[2];
    assertThatThrownBy(() -> service.decoder().decode(altered)).isInstanceOf(JwtException.class);
    String unsigned = "eyJhbGciOiJub25lIn0." + parts[1] + ".";
    assertThatThrownBy(() -> service.decoder().decode(unsigned)).isInstanceOf(JwtException.class);
    for (String invalid :
        new String[] {
          sign(base().issuer("wrong").build(), "test-kid"),
          sign(base().audience("wrong").build(), "test-kid"),
          sign(
              base()
                  .issueTime(Date.from(now.minusSeconds(700)))
                  .expirationTime(Date.from(now.minusSeconds(60)))
                  .build(),
              "test-kid"),
          sign(base().build(), "unknown-kid"),
          sign(base().claim("sid", null).build(), "test-kid"),
          sign(base().jwtID(null).build(), "test-kid")
        }) {
      assertThatThrownBy(() -> service.decoder().decode(invalid)).isInstanceOf(JwtException.class);
    }
  }

  @Test
  void futureIssuedNotBeforeAndOverlongLifetimesAreRejected() throws Exception {
    for (var claims :
        new JWTClaimsSet[] {
          base().issueTime(Date.from(now.plusSeconds(60))).build(),
          base().notBeforeTime(Date.from(now.plusSeconds(60))).build(),
          base().expirationTime(Date.from(now.plusSeconds(601))).build()
        }) {
      String invalid = sign(claims, "test-kid");
      assertThatThrownBy(() -> service.decoder().decode(invalid)).isInstanceOf(JwtException.class);
    }
  }

  private JWTClaimsSet.Builder base() {
    return new JWTClaimsSet.Builder()
        .issuer("urn:kimchimap:auth")
        .audience("kimchimap-api")
        .subject(UUID.randomUUID().toString())
        .issueTime(Date.from(now))
        .expirationTime(Date.from(now.plusSeconds(600)))
        .jwtID(UUID.randomUUID().toString())
        .claim("sid", UUID.randomUUID().toString())
        .claim("ver", 0L);
  }

  @Test
  void hmacAlgorithmConfusionAndMissingProductionKeyAreRejected() throws Exception {
    var hmac =
        new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.HS256).keyID("test-kid").build(), base().build());
    hmac.sign(new com.nimbusds.jose.crypto.MACSigner(new byte[32]));
    assertThatThrownBy(() -> service.decoder().decode(hmac.serialize()))
        .isInstanceOf(JwtException.class);
    var environment = new MockEnvironment();
    environment.setActiveProfiles("prod");
    assertThatThrownBy(
            () ->
                new JwtService(
                    environment,
                    Clock.fixed(now, ZoneOffset.UTC),
                    "urn:kimchimap:auth",
                    "kimchimap-api",
                    Duration.ofMinutes(10)))
        .isInstanceOf(IllegalStateException.class);
  }

  private String sign(JWTClaimsSet claims, String kid) throws Exception {
    var jwt =
        new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(kid).type(JOSEObjectType.JWT).build(),
            claims);
    jwt.sign(new RSASSASigner(signing));
    return jwt.serialize();
  }
}
