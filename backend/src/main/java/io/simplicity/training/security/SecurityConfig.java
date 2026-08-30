package io.simplicity.training.security;

import io.simplicity.training.config.AppProperties;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Stateless resource-server security. Cognito issues the tokens; this application only validates
 * them and resolves them to an application principal.
 */
@Configuration
@EnableConfigurationProperties(AppProperties.class)
@EnableMethodSecurity
public class SecurityConfig {

  private static final String[] PUBLIC_PATHS = {
    "/actuator/health", "/actuator/health/**", "/actuator/info",
    "/v3/api-docs", "/v3/api-docs.yaml", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**"
  };

  @Bean
  SecurityFilterChain securityFilterChain(
      HttpSecurity http, PrincipalResolutionFilter principalResolutionFilter) throws Exception {
    return http.csrf(csrf -> csrf.disable())
        .cors(Customizer.withDefaults())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(HttpMethod.OPTIONS, "/**")
                    .permitAll()
                    .requestMatchers(PUBLIC_PATHS)
                    .permitAll()
                    // Invitation preview is deliberately public: the recipient has to see who
                    // invited them before deciding to create an account. The unguessable token is
                    // the only credential, and it reveals nothing beyond the organisation name.
                    .requestMatchers(HttpMethod.GET, "/api/v1/invitations/*")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
        // After the bearer token has been authenticated, before authorisation is evaluated.
        .addFilterBefore(principalResolutionFilter, AuthorizationFilter.class)
        .build();
  }

  /**
   * Built from the JWKS URI rather than the issuer URI so that no network call happens during
   * startup. Tests supply their own decoder bean and never reach Cognito.
   */
  @Bean
  @ConditionalOnMissingBean(JwtDecoder.class)
  public JwtDecoder jwtDecoder(AppProperties properties) {
    String jwkSetUri = properties.cognito().jwkSetUri();
    if (jwkSetUri == null) {
      throw new IllegalStateException(
          "app.cognito.issuer-uri must be set, or a JwtDecoder bean supplied, before the "
              + "application can validate access tokens");
    }
    NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
    decoder.setJwtValidator(cognitoValidator(properties));
    return decoder;
  }

  /**
   * What the built-in decoder does not check.
   *
   * <p>{@code withJwkSetUri(...).build()} validates the signature and the expiry, and stops there.
   * Cognito signs ID tokens with the same keys as access tokens, so without {@code token_use} an ID
   * token — which clients pass around far more freely, and which is not meant to authorise anything
   * — is accepted as a bearer credential.
   */
  public   static OAuth2TokenValidator<Jwt> cognitoValidator(AppProperties properties) {
    List<String> clientIds = properties.cognito().clientIds();
    if (clientIds == null || clientIds.isEmpty()) {
      // Refuse to start rather than reject every request. An empty list would make `contains`
      // false for every token, so a missing COGNITO_CLIENT_IDS would present as a total outage
      // with no obvious cause instead of a startup failure naming the setting.
      throw new IllegalStateException(
          "app.cognito.client-ids must list every Cognito app client the pool issues access "
              + "tokens to. With none configured, every request would be rejected.");
    }

    return new DelegatingOAuth2TokenValidator<>(
        new JwtTimestampValidator(),
        new JwtIssuerValidator(properties.cognito().issuerUri()),
        new JwtClaimValidator<String>("token_use", "access"::equals),
        // Missing claims reach the predicate as null, so every predicate here refuses null rather
        // than assuming the claim is present.
        new JwtClaimValidator<String>("client_id", clientIds::contains));
  }

  @Bean
  CorsConfigurationSource corsConfigurationSource(AppProperties properties) {
    List<String> origins = properties.web().corsAllowedOrigins();
    CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedOrigins(origins == null ? List.of() : origins);
    configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Org-Id"));
    configuration.setMaxAge(3600L);
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/api/**", configuration);
    return source;
  }
}
