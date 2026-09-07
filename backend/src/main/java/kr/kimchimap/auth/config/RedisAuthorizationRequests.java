package kr.kimchimap.auth.config;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import kr.kimchimap.auth.repository.OAuthRequestRepository;
import kr.kimchimap.auth.service.TokenSecrets;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseCookie;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class RedisAuthorizationRequests
    implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {
  private final OAuthRequestRepository requests;
  private final ObjectMapper json;
  private final boolean secure;
  private final String cookieName;

  public RedisAuthorizationRequests(
      OAuthRequestRepository requests, ObjectMapper json, Environment environment) {
    this.requests = requests;
    this.json = json;
    secure = environment.matchesProfiles("prod");
    cookieName = secure ? "__Host-km-oauth" : "km-local-oauth";
  }

  @Override
  public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
    String key = key(request);
    return key == null ? null : decode(requests.read(key));
  }

  @Override
  public OAuth2AuthorizationRequest removeAuthorizationRequest(
      HttpServletRequest request, HttpServletResponse response) {
    String key = key(request);
    clear(response);
    // state와 브라우저 비밀이 함께 일치할 때만 일회성 요청을 소비한다.
    return key == null ? null : decode(requests.consume(key));
  }

  @Override
  public void saveAuthorizationRequest(
      OAuth2AuthorizationRequest authorization,
      HttpServletRequest request,
      HttpServletResponse response) {
    if (authorization == null) {
      removeAuthorizationRequest(request, response);
      return;
    }
    String binding = TokenSecrets.generate();
    var stored =
        new StoredRequest(
            authorization.getAuthorizationUri(),
            authorization.getClientId(),
            authorization.getRedirectUri(),
            authorization.getScopes(),
            authorization.getState(),
            strings(authorization.getAttributes()),
            strings(authorization.getAdditionalParameters()));
    requests.save(
        TokenSecrets.hash(authorization.getState() + ":" + binding),
        json.writeValueAsString(stored));
    writeCookie(response, binding, Duration.ofMinutes(10));
  }

  public void clear(HttpServletResponse response) {
    writeCookie(response, "", Duration.ZERO);
  }

  private void writeCookie(HttpServletResponse response, String value, Duration lifetime) {
    response.addHeader(
        "Set-Cookie",
        ResponseCookie.from(cookieName, value)
            .httpOnly(true)
            .secure(secure)
            .sameSite("Lax")
            .path("/")
            .maxAge(lifetime)
            .build()
            .toString());
    response.setHeader("Cache-Control", "no-store");
  }

  private String key(HttpServletRequest request) {
    String[] states = request.getParameterValues("state");
    if (states == null
        || states.length != 1
        || states[0].length() > 200
        || request.getCookies() == null) return null;
    var bindings =
        Arrays.stream(request.getCookies())
            .filter(c -> c.getName().equals(cookieName))
            .map(Cookie::getValue)
            .toList();
    if (bindings.size() != 1 || !bindings.getFirst().matches("[A-Za-z0-9_-]{43}")) return null;
    return TokenSecrets.hash(states[0] + ":" + bindings.getFirst());
  }

  private OAuth2AuthorizationRequest decode(String encoded) {
    if (encoded == null) return null;
    var stored = json.readValue(encoded, StoredRequest.class);
    return OAuth2AuthorizationRequest.authorizationCode()
        .authorizationUri(stored.authorizationUri())
        .clientId(stored.clientId())
        .redirectUri(stored.redirectUri())
        .scopes(stored.scopes())
        .state(stored.state())
        .attributes(new LinkedHashMap<>(stored.attributes()))
        .additionalParameters(new LinkedHashMap<>(stored.parameters()))
        .build();
  }

  private static Map<String, String> strings(Map<String, Object> values) {
    Map<String, String> result = new LinkedHashMap<>();
    values.forEach(
        (key, value) -> {
          if (!(value instanceof String text))
            throw new IllegalStateException("인가 요청 형식이 지원되지 않습니다.");
          result.put(key, text);
        });
    return result;
  }

  private record StoredRequest(
      String authorizationUri,
      String clientId,
      String redirectUri,
      Set<String> scopes,
      String state,
      Map<String, String> attributes,
      Map<String, String> parameters) {
    @Override
    public String toString() {
      return "StoredRequest[비공개]";
    }
  }
}
