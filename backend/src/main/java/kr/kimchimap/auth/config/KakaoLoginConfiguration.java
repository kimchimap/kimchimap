package kr.kimchimap.auth.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import kr.kimchimap.auth.service.AuthCookies;
import kr.kimchimap.auth.service.SessionService;
import kr.kimchimap.member.service.MemberService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.endpoint.RestClientAuthorizationCodeTokenResponseClient;
import org.springframework.security.oauth2.client.http.OAuth2ErrorResponseErrorHandler;
import org.springframework.security.oauth2.client.oidc.authentication.OidcIdTokenDecoderFactory;
import org.springframework.security.oauth2.client.oidc.authentication.OidcIdTokenValidator;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.http.converter.OAuth2AccessTokenResponseHttpMessageConverter;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoderFactory;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.context.NullSecurityContextRepository;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

@Configuration
public class KakaoLoginConfiguration {
  @Bean
  JwtDecoderFactory<ClientRegistration> oidcDecoders() {
    var decoders = new ConcurrentHashMap<String, JwtDecoder>();
    return registration ->
        decoders.computeIfAbsent(
            registration.getRegistrationId(),
            key -> {
              var decoder =
                  NimbusJwtDecoder.withJwkSetUri(registration.getProviderDetails().getJwkSetUri())
                      .jwsAlgorithm(SignatureAlgorithm.RS256)
                      .restOperations(new RestTemplate(httpFactory()))
                      .build();
              decoder.setClaimSetConverter(
                  OidcIdTokenDecoderFactory.createDefaultClaimTypeConverter());
              decoder.setJwtValidator(
                  new DelegatingOAuth2TokenValidator<>(
                      new JwtTimestampValidator(Duration.ofSeconds(30)),
                      new OidcIdTokenValidator(registration)));
              return decoder;
            });
  }

  private static JdkClientHttpRequestFactory httpFactory() {
    var factory =
        new JdkClientHttpRequestFactory(
            HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build());
    factory.setReadTimeout(Duration.ofSeconds(10));
    return factory;
  }

  @Bean
  @ConditionalOnMissingBean(ClientRegistrationRepository.class)
  ClientRegistrationRepository kakaoClients(Environment environment) {
    String id = environment.getProperty("KAKAO_CLIENT_ID", "");
    String secret = environment.getProperty("KAKAO_CLIENT_SECRET", "");
    if (id.isBlank() || secret.isBlank()) return registrationId -> null;
    String origin = environment.getProperty("app.public-origin", "http://localhost:5173");
    return new InMemoryClientRegistrationRepository(
        ClientRegistration.withRegistrationId("kakao")
            .clientName("카카오")
            .clientId(id)
            .clientSecret(secret)
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri(origin + "/api/v1/auth/callback/kakao")
            .scope("openid")
            .authorizationUri("https://kauth.kakao.com/oauth/authorize")
            .tokenUri("https://kauth.kakao.com/oauth/token")
            .jwkSetUri("https://kauth.kakao.com/.well-known/jwks.json")
            .issuerUri("https://kauth.kakao.com")
            .userInfoUri("https://kapi.kakao.com/v1/oidc/userinfo")
            .userNameAttributeName("sub")
            .build());
  }

  public void configure(
      HttpSecurity http,
      ClientRegistrationRepository clients,
      RedisAuthorizationRequests requests,
      MemberService members,
      SessionService sessions,
      AuthCookies cookies,
      Environment environment)
      throws Exception {
    if (clients.findByRegistrationId("kakao") == null) return;
    var resolver = new DefaultOAuth2AuthorizationRequestResolver(clients, "/api/v1/auth/login");
    resolver.setAuthorizationRequestCustomizer(OAuth2AuthorizationRequestCustomizers.withPkce());
    var tokenClient = new RestClientAuthorizationCodeTokenResponseClient();
    tokenClient.setRestClient(
        RestClient.builder()
            .requestFactory(httpFactory())
            .configureMessageConverters(
                converters ->
                    converters
                        .disableDefaults()
                        .addCustomConverter(new FormHttpMessageConverter())
                        .addCustomConverter(new OAuth2AccessTokenResponseHttpMessageConverter()))
            .defaultStatusHandler(new OAuth2ErrorResponseErrorHandler())
            .build());
    var users = new OidcUserService();
    users.setRetrieveUserInfo(request -> false);
    String origin = environment.getProperty("app.public-origin", "http://localhost:5173");
    http.oauth2Login(
        oauth ->
            oauth
                .clientRegistrationRepository(clients)
                .authorizedClientRepository(new DiscardedAuthorizedClients())
                .securityContextRepository(new NullSecurityContextRepository())
                .authorizationEndpoint(
                    endpoint ->
                        endpoint
                            .authorizationRequestResolver(resolver)
                            .authorizationRequestRepository(requests))
                .redirectionEndpoint(endpoint -> endpoint.baseUri("/api/v1/auth/callback/*"))
                .tokenEndpoint(endpoint -> endpoint.accessTokenResponseClient(tokenClient))
                .userInfoEndpoint(endpoint -> endpoint.oidcUserService(users))
                .successHandler(
                    (request, response, authentication) -> {
                      try {
                        var identity = (OidcUser) authentication.getPrincipal();
                        var issued =
                            sessions.create(
                                members.loginWithVerifiedKakaoSubject(identity.getSubject()));
                        cookies.set(response, issued);
                        requests.clear(response);
                        response.sendRedirect(origin + "/auth/complete");
                      } catch (RuntimeException exception) {
                        requests.clear(response);
                        response.sendRedirect(origin + "/login?error=unavailable");
                      } finally {
                        SecurityContextHolder.clearContext();
                      }
                    })
                .failureHandler(
                    (request, response, exception) -> {
                      requests.clear(response);
                      response.sendRedirect(origin + "/login?error=failed");
                    }));
  }

  private static final class DiscardedAuthorizedClients
      implements OAuth2AuthorizedClientRepository {
    @Override
    public <T extends OAuth2AuthorizedClient> T loadAuthorizedClient(
        String registrationId, Authentication principal, HttpServletRequest request) {
      return null;
    }

    @Override
    public void saveAuthorizedClient(
        OAuth2AuthorizedClient client,
        Authentication principal,
        HttpServletRequest request,
        HttpServletResponse response) {
      // 회원 식별 후 공급자 API를 호출하지 않으므로 공급자 토큰을 보관하지 않는다.
    }

    @Override
    public void removeAuthorizedClient(
        String registrationId,
        Authentication principal,
        HttpServletRequest request,
        HttpServletResponse response) {}
  }
}
