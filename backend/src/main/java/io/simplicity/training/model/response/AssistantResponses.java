package io.simplicity.training.model.response;

import java.util.List;
import java.util.UUID;

public final class AssistantResponses {

  private AssistantResponses() {}

  /**
   * @param answered false when the training does not cover the question, in which case {@code
   *     answer} is the standard wording and no model was called
   */
  public record AnswerResponse(boolean answered, String answer, List<CitationResponse> citations) {}

  /**
   * @param assignedToYou whether the caller can actually open this module in Learn. Retrieval spans
   *     the organisation, so a citation may name something they cannot reach.
   */
  public record CitationResponse(
      UUID moduleId, String moduleTitle, String sectionTitle, boolean assignedToYou) {}
}
