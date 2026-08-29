package io.simplicity.training.config;

import io.simplicity.training.service.assistant.AnswerGenerator;
import io.simplicity.training.service.assistant.BedrockAnswerGenerator;
import io.simplicity.training.service.assistant.BedrockEmbedder;
import io.simplicity.training.service.assistant.Embedder;
import java.net.URI;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClientBuilder;

/**
 * Bedrock, in the same region as everything else so training content does not leave it.
 *
 * <p>The suite overrides both beans with {@code @Primary} fakes rather than suppressing these
 * conditionally, so this wiring is exercised on every context load.
 */
@Configuration
public class AssistantConfig {

  @Bean
  public BedrockRuntimeClient bedrockClient(AppProperties properties) {
    BedrockRuntimeClientBuilder builder =
        BedrockRuntimeClient.builder().region(Region.of(properties.cognito().region()));
    properties.aws().endpoint().ifPresent((URI endpoint) -> builder.endpointOverride(endpoint));
    return builder.build();
  }

  @Bean
  public Embedder embedder(BedrockRuntimeClient bedrock) {
    return new BedrockEmbedder(bedrock);
  }

  @Bean
  public AnswerGenerator answerGenerator(BedrockRuntimeClient bedrock) {
    return new BedrockAnswerGenerator(bedrock);
  }
}
