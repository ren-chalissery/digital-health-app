package io.simplicity.training.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sesv2.SesV2Client;

/**
 * The SES client is only created when mail is enabled, so local runs and tests never attempt to
 * resolve AWS credentials.
 */
@Configuration
@ConditionalOnProperty(name = "app.mail.enabled", havingValue = "true")
public class AwsConfig {

  @Bean
  SesV2Client sesClient(AppProperties properties) {
    // Credentials come from the default chain, which on Fargate resolves the task role.
    return SesV2Client.builder().region(Region.of(properties.cognito().region())).build();
  }
}
