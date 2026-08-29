package io.simplicity.training.config;

import java.net.URI;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.awscore.client.builder.AwsClientBuilder;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.sesv2.SesV2Client;

/**
 * AWS clients. Credentials come from the default chain, which on Fargate resolves the task role.
 *
 * <p>Both clients honour {@code app.aws.endpoint-override} so a local run can point them at an
 * emulator. The property is unset everywhere it is deployed, leaving the SDK to resolve real
 * endpoints.
 */
@Configuration
public class AwsConfig {

  @Bean
  @ConditionalOnMissingBean
  public CognitoIdentityProviderClient cognitoClient(AppProperties properties) {
    return configure(CognitoIdentityProviderClient.builder(), properties).build();
  }

  /** Only created when mail is enabled, so local runs never try to send anything by accident. */
  @Bean
  @ConditionalOnProperty(name = "app.mail.enabled", havingValue = "true")
  @ConditionalOnMissingBean
  public SesV2Client sesClient(AppProperties properties) {
    return configure(SesV2Client.builder(), properties).build();
  }

  private <B extends AwsClientBuilder<B, ?>> B configure(B builder, AppProperties properties) {
    builder.region(Region.of(properties.cognito().region()));
    properties.aws().endpoint().ifPresent((URI endpoint) -> builder.endpointOverride(endpoint));
    return builder;
  }
}
