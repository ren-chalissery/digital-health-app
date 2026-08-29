package io.simplicity.training.config;

import io.simplicity.training.service.media.MediaConvertTranscoder;
import io.simplicity.training.service.media.ObjectStore;
import io.simplicity.training.service.media.S3ObjectStore;
import io.simplicity.training.service.media.Transcoder;
import java.net.URI;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import software.amazon.awssdk.awscore.client.builder.AwsClientBuilder;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.mediaconvert.MediaConvertClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * Video clients.
 *
 * <p>The suite overrides both with {@code @Primary} fakes rather than suppressing them
 * conditionally, so this wiring is still exercised on every context load. Unlike SES and Cognito,
 * MediaConvert has no local emulator to run the real ones against offline.
 */
@Configuration
@EnableScheduling
public class MediaConfig {

  @Bean
  public ObjectStore objectStore(AppProperties properties) {
    S3Client s3 = configure(S3Client.builder(), properties).build();
    S3Presigner.Builder presigner = S3Presigner.builder().region(region(properties));
    properties.aws().endpoint().ifPresent(presigner::endpointOverride);
    return new S3ObjectStore(s3, presigner.build());
  }

  @Bean
  public Transcoder transcoder(AppProperties properties) {
    return new MediaConvertTranscoder(
        configure(MediaConvertClient.builder(), properties).build(), properties);
  }

  private Region region(AppProperties properties) {
    return Region.of(properties.cognito().region());
  }

  private <B extends AwsClientBuilder<B, ?>> B configure(B builder, AppProperties properties) {
    builder.region(region(properties));
    properties.aws().endpoint().ifPresent((URI endpoint) -> builder.endpointOverride(endpoint));
    return builder;
  }
}
