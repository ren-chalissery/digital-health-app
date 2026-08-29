package io.simplicity.training.service.assistant;

import java.util.List;

/**
 * Writes an answer from passages that were retrieved for it.
 *
 * <p>It is never asked a question without passages. Whether there is an answer at all is decided
 * before this is reached, by a similarity threshold, because a prompt asking a model to stay within
 * its sources is a request and a threshold is a gate.
 */
public interface AnswerGenerator {

  String answer(String question, List<Passage> passages);

  /** One retrieved piece of a module, with enough context for the model to attribute it. */
  record Passage(String moduleTitle, String sectionTitle, String content) {}
}
