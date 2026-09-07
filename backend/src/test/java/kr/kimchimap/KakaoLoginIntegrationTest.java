package kr.kimchimap;

import static org.assertj.core.api.Assertions.assertThat;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import tools.jackson.databind.ObjectMapper;

@Import(KakaoLoginIntegrationTest.ProviderConfiguration.class)
class KakaoLoginIntegrationTest extends ApplicationIntegrationSupport {
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final Map<String, Grant> GRANTS = new ConcurrentHashMap<>();
  private static final AtomicInteger EXCHANGES = new AtomicInteger();
  private static final RSAKey KEY;
  private static final HttpServer PROVIDER;
  private static final String ISSUER;

  static {
    try {
      KEY = new RSAKeyGenerator(2048).keyID("test-oidc").generate();
      PROVIDER = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      ISSUER = "http://127.0.0.1:" + PROVIDER.getAddress().getPort();
      PROVIDER.createContext(
          "/jwks",
          exchange -> {
            byte[] body = new JWKSet(KEY.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
          });
      PROVIDER.createContext(
          "/token",
          exchange -> {
            EXCHANGES.incrementAndGet();
            try {
              Map<String, String> form =
                  parameters(
                      new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
              Grant grant = GRANTS.remove(form.get("code"));
              String challenge =
                  Base64.getUrlEncoder()
                      .withoutPadding()
                      .encodeToString(
                          MessageDigest.getInstance("SHA-256")
                              .digest(
                                  form.getOrDefault("code_verifier", "")
                                      .getBytes(StandardCharsets.US_ASCII)));
              if (grant == null
                  || !grant.challenge().equals(challenge)
                  || !"test-client".equals(form.get("client_id"))
                  || !"test-client-secret".equals(form.get("client_secret"))
                  || !"http://localhost:5173/api/v1/auth/callback/kakao"
                      .equals(form.get("redirect_uri"))) {
                throw new IllegalArgumentException("테스트 인가 요청 불일치");
              }
              Instant now = Instant.now();
              var claims =
                  new JWTClaimsSet.Builder()
                      .issuer(grant.failure().equals("issuer") ? ISSUER + "/wrong" : ISSUER)
                      .subject(grant.subject())
                      .audience(grant.failure().equals("audience") ? "other" : "test-client")
                      .issueTime(Date.from(now))
                      .expirationTime(
                          Date.from(
                              now.plusSeconds(grant.failure().equals("expired") ? -120 : 300)))
                      .claim("nonce", grant.failure().equals("nonce") ? "wrong" : grant.nonce())
                      .build();
              var jwt =
                  new SignedJWT(
                      new JWSHeader.Builder(JWSAlgorithm.RS256)
                          .keyID("test-oidc")
                          .type(JOSEObjectType.JWT)
                          .build(),
                      claims);
              jwt.sign(
                  new RSASSASigner(
                      grant.failure().equals("signature")
                          ? new RSAKeyGenerator(2048).generate()
                          : KEY));
              byte[] body =
                  JSON.writeValueAsBytes(
                      Map.of(
                          "access_token",
                          "test-provider-token",
                          "token_type",
                          "Bearer",
                          "expires_in",
                          300,
                          "id_token",
                          jwt.serialize()));
              exchange.getResponseHeaders().set("Content-Type", "application/json");
              exchange.sendResponseHeaders(200, body.length);
              exchange.getResponseBody().write(body);
            } catch (Exception exception) {
              byte[] body = "{\"error\":\"invalid_grant\"}".getBytes(StandardCharsets.UTF_8);
              exchange.getResponseHeaders().set("Content-Type", "application/json");
              exchange.sendResponseHeaders(400, body.length);
              exchange.getResponseBody().write(body);
            } finally {
              exchange.close();
            }
          });
      PROVIDER.start();
    } catch (Exception exception) {
      throw new ExceptionInInitializerError(exception);
    }
  }

  @AfterAll
  static void stopProvider() {
    PROVIDER.stop(0);
  }

  @Test
  void exchangesCodeWithPkceAndOidcThenIssuesOnlyServiceCookie() throws Exception {
    var flow = begin("");
    assertThat(flow.parameters())
        .containsEntry("code_challenge_method", "S256")
        .containsEntry("scope", "openid");
    assertThat(flow.parameters().get("nonce")).isNotBlank();
    var response = callback(flow, flow.cookie());
    assertThat(response.statusCode()).isEqualTo(302);
    assertThat(response.headers().firstValue("location"))
        .contains("http://localhost:5173/auth/complete");
    assertThat(response.headers().allValues("set-cookie"))
        .anyMatch(value -> value.startsWith("km-local-refresh=") && value.contains("HttpOnly"));
    assertThat(response.headers().allValues("set-cookie"))
        .noneMatch(value -> value.contains("JSESSIONID"));
    assertThat(
            jdbc.queryForObject(
                "select count(*) from app.member where provider_subject = ? and role = 'USER'",
                Integer.class,
                flow.subject()))
        .isEqualTo(1);
    assertThat(callback(flow, flow.cookie()).headers().firstValue("location"))
        .contains("http://localhost:5173/login?error=failed");
    assertThat(redis.keys("km:oauth:*")).isEmpty();
    assertThat(response.body()).doesNotContain("test-provider-token");
  }

  @Test
  void rejectsStateAndBrowserMismatchWithoutConsumingLegitimateFlow() throws Exception {
    var flow = begin("");
    int before = EXCHANGES.get();
    assertThat(callback(flow, "km-local-oauth=" + "a".repeat(43)).headers().firstValue("location"))
        .contains("http://localhost:5173/login?error=failed");
    var wrongState =
        new Flow(flow.parameters(), flow.cookie(), flow.code(), flow.subject(), "other-state");
    assertThat(callback(wrongState, flow.cookie()).headers().firstValue("location"))
        .contains("http://localhost:5173/login?error=failed");
    assertThat(EXCHANGES.get()).isEqualTo(before);
    assertThat(callback(flow, flow.cookie()).headers().firstValue("location"))
        .contains("http://localhost:5173/auth/complete");
  }

  @Test
  void rejectsInvalidIdTokensWithoutCreatingMembersOrSessions() throws Exception {
    for (String failure : new String[] {"nonce", "issuer", "audience", "expired", "signature"}) {
      var flow = begin(failure);
      var response = callback(flow, flow.cookie());
      assertThat(response.headers().firstValue("location"))
          .as(failure)
          .contains("http://localhost:5173/login?error=failed");
      assertThat(response.headers().allValues("set-cookie"))
          .noneMatch(value -> value.startsWith("km-local-refresh="));
      assertThat(
              jdbc.queryForObject(
                  "select count(*) from app.member where provider_subject = ?",
                  Integer.class,
                  flow.subject()))
          .isZero();
    }
  }

  @Test
  void refusesLoginWhenRedisCannotTrackState() throws Exception {
    REDIS.getDockerClient().pauseContainerCmd(REDIS.getContainerId()).exec();
    try {
      var response = get("/api/v1/auth/login/kakao");
      assertThat(response.statusCode()).isEqualTo(503);
      assertThat(response.body()).contains("AUTHENTICATION_UNAVAILABLE");
      assertThat(get("/api/v1/catalogs/ingredients").statusCode()).isEqualTo(200);
    } finally {
      REDIS.getDockerClient().unpauseContainerCmd(REDIS.getContainerId()).exec();
    }
  }

  @Test
  void rejectsInvalidCodeVerifierAndExpiresAuthorizationState() throws Exception {
    var flow = begin("");
    GRANTS.put(
        flow.code(),
        new Grant("different-challenge", flow.parameters().get("nonce"), flow.subject(), ""));
    assertThat(callback(flow, flow.cookie()).headers().firstValue("location"))
        .contains("http://localhost:5173/login?error=failed");
    var expiring = begin("");
    var keys = redis.keys("km:oauth:*");
    assertThat(keys).hasSize(1);
    for (String key : keys) {
      assertThat(redis.getExpire(key)).isBetween(1L, 600L);
      redis.delete(key);
    }
    assertThat(callback(expiring, expiring.cookie()).headers().firstValue("location"))
        .contains("http://localhost:5173/login?error=failed");
  }

  private Flow begin(String failure) throws Exception {
    var response = get("/api/v1/auth/login/kakao");
    assertThat(response.statusCode()).isEqualTo(302);
    Map<String, String> parameters =
        parameters(
            URI.create(response.headers().firstValue("location").orElseThrow()).getRawQuery());
    String cookie =
        response.headers().allValues("set-cookie").stream()
            .filter(value -> value.startsWith("km-local-oauth="))
            .findFirst()
            .orElseThrow()
            .split(";", 2)[0];
    String code = UUID.randomUUID().toString();
    String subject = "test-kakao-" + UUID.randomUUID();
    GRANTS.put(
        code,
        new Grant(parameters.get("code_challenge"), parameters.get("nonce"), subject, failure));
    return new Flow(parameters, cookie, code, subject, parameters.get("state"));
  }

  private HttpResponse<String> callback(Flow flow, String cookie) throws Exception {
    return client.send(
        HttpRequest.newBuilder(
                URI.create(
                    base()
                        + "/api/v1/auth/callback/kakao?code="
                        + flow.code()
                        + "&state="
                        + URLEncoder.encode(flow.state(), StandardCharsets.UTF_8)))
            .header("Cookie", cookie)
            .GET()
            .build(),
        HttpResponse.BodyHandlers.ofString());
  }

  private static Map<String, String> parameters(String value) {
    Map<String, String> result = new HashMap<>();
    for (String part : value.split("&")) {
      String[] pair = part.split("=", 2);
      result.put(
          URLDecoder.decode(pair[0], StandardCharsets.UTF_8),
          URLDecoder.decode(pair.length == 2 ? pair[1] : "", StandardCharsets.UTF_8));
    }
    return result;
  }

  private record Flow(
      Map<String, String> parameters, String cookie, String code, String subject, String state) {}

  private record Grant(String challenge, String nonce, String subject, String failure) {}

  @TestConfiguration
  static class ProviderConfiguration {
    @Bean
    @Primary
    ClientRegistrationRepository controlledProvider() {
      return new InMemoryClientRegistrationRepository(
          ClientRegistration.withRegistrationId("kakao")
              .clientId("test-client")
              .clientSecret("test-client-secret")
              .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
              .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
              .scope("openid")
              .redirectUri("http://localhost:5173/api/v1/auth/callback/kakao")
              .authorizationUri(ISSUER + "/authorize")
              .tokenUri(ISSUER + "/token")
              .jwkSetUri(ISSUER + "/jwks")
              .issuerUri(ISSUER)
              .userInfoUri(ISSUER + "/userinfo")
              .userNameAttributeName("sub")
              .build());
    }
  }
}
