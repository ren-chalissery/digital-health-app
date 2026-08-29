package io.simplicity.training.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Strongly typed view of the {@code app.*} configuration tree. */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
    Cognito cognito, Auth auth, Invitations invitations, Mail mail, Web web) {

  public record Cognito(
      String issuerUri, String userPoolId, String clientId, @DefaultValue("ap-southeast-2") String region) {

    /** Cognito always publishes its keys at this well-known location. */
    public String jwkSetUri() {
      return issuerUri == null || issuerUri.isBlank() ? null : issuerUri + "/.well-known/jwks.json";
    }
  }

  public record Auth(
      @DefaultValue("15m") Duration accessTokenTtl, @DefaultValue("5m") Duration principalCacheTtl) {}

  public record Invitations(
      @DefaultValue("7d") Duration ttl, @DefaultValue("50") int maxPerHourPerOrg) {}

  public record Mail(String from, @DefaultValue("false") boolean enabled) {}

  public record Web(String baseUrl, @DefaultValue("") List<String> corsAllowedOrigins) {}
}
