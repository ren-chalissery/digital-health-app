package io.simplicity.training.support;

import io.simplicity.training.service.assistant.AnswerGenerator;
import io.simplicity.training.service.assistant.Embedder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Stands in for Bedrock, which has no local emulator.
 *
 * <p>The embedder is a bag of words rather than a random vector, so text sharing words lands close
 * together and text sharing none lands far apart. That is enough to assert what retrieval must do:
 * find the relevant passage, and decline when nothing is relevant.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestAssistantConfiguration {

  @Bean
  @Primary
  public DeterministicEmbedder deterministicEmbedder() {
    return new DeterministicEmbedder();
  }

  @Bean
  @Primary
  public RecordingAnswerGenerator recordingAnswerGenerator() {
    return new RecordingAnswerGenerator();
  }

  /**
   * Hashes each word into one of the 1024 dimensions and normalises, so cosine distance behaves the
   * way a real embedder's does for the purposes of these tests: shared vocabulary is near, disjoint
   * vocabulary is far.
   */
  public static class DeterministicEmbedder implements Embedder {

    private static final int DIMENSIONS = 1024;

    @Override
    public float[] embed(String text) {
      float[] vector = new float[DIMENSIONS];
      for (String word : text.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
        if (!word.isBlank()) {
          vector[Math.floorMod(word.hashCode(), DIMENSIONS)] += 1f;
        }
      }
      double length = 0;
      for (float value : vector) {
        length += value * value;
      }
      length = Math.sqrt(length);
      if (length > 0) {
        for (int i = 0; i < DIMENSIONS; i++) {
          vector[i] /= (float) length;
        }
      }
      return vector;
    }
  }

  /** Echoes the passages it was given, so a test can see exactly what reached the model. */
  public static class RecordingAnswerGenerator implements AnswerGenerator {

    public final List<List<Passage>> calls = new ArrayList<>();

    @Override
    public String answer(String question, List<Passage> passages) {
      calls.add(List.copyOf(passages));
      return "Answered from: "
          + passages.stream().map(Passage::moduleTitle).distinct().reduce("", (a, b) -> a + b);
    }

    public void reset() {
      calls.clear();
    }
  }
}
