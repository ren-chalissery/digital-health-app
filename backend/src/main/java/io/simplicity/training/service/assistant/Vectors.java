package io.simplicity.training.service.assistant;

import java.util.StringJoiner;

/** pgvector's text form, which is simply the numbers in square brackets. */
final class Vectors {

  private Vectors() {}

  static String toLiteral(float[] vector) {
    StringJoiner joiner = new StringJoiner(",", "[", "]");
    for (float value : vector) {
      joiner.add(Float.toString(value));
    }
    return joiner.toString();
  }
}
