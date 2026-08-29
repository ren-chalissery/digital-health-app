package io.simplicity.training.service.assistant;

import java.util.List;

/**
 * Turns text into a vector.
 *
 * <p>Behind an interface because Bedrock has no local emulator, and because the suite needs
 * deterministic vectors to assert nearest-neighbour ordering against.
 */
public interface Embedder {

  /** @return a vector of exactly the dimension the schema declares */
  float[] embed(String text);

  default List<float[]> embedAll(List<String> texts) {
    return texts.stream().map(this::embed).toList();
  }
}
