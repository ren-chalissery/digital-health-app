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
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
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
    "/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**"
  };

  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
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
        .build();
  }

  /**
   * Built from the JWKS URI rather than the issuer URI so that no network call happens during
   * startup. Tests supply their own decoder bean and never reach Cognito.
   */
  @Bean
  @ConditionalOnMissingBean(JwtDecoder.class)
  JwtDecoder jwtDecoder(AppProperties properties) {
    String jwkSetUri = properties.cognito().jwkSetUri();
    if (jwkSetUri == null) {
      throw new IllegalStateException(
          "app.cognito.issuer-uri must be set, or a JwtDecoder bean supplied, before the "
              + "application can validate access tokens");
    }
    return NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
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
