package io.simplicity.training.config;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Strongly typed view of the {@code app.*} configuration tree. */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
    Aws aws,
    Cognito cognito,
    Auth auth,
    Invitations invitations,
    Mail mail,
    Media media,
    Web web) {

  /**
   * @param uploadBucket empty until the media stack exists, which makes video unavailable and
   *     leaves everything else working
   */
  public record Media(
      String uploadBucket,
      String assetBucket,
      String transcodeQueueArn,
      String transcodeRoleArn,
      @DefaultValue("15m") Duration playbackUrlTtl,
      @DefaultValue("524288000") long maxUploadBytes) {

    public boolean isConfigured() {
      return uploadBucket != null && !uploadBucket.isBlank();
    }
  }

  /**
   * @param endpointOverride points the AWS clients at a local emulator. Never set in a deployed
   *     environment, where the SDK's own endpoint resolution is what we want.
   */
  public record Aws(String endpointOverride) {

    public Optional<URI> endpoint() {
      return endpointOverride == null || endpointOverride.isBlank()
          ? Optional.empty()
          : Optional.of(URI.create(endpointOverride));
    }
  }

  public record Cognito(
      String issuerUri,
      String userPoolId,
      // A list, not a value. The pool issues tokens to a web, an iOS and an Android client, and
      // validating against only one of them would reject the other two outright.
      List<String> clientIds,
      @DefaultValue("ap-southeast-2") String region) {

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
