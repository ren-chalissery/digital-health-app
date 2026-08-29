package io.simplicity.training.model.response;

import java.util.List;
import java.util.UUID;

/**
 * Quiz payloads.
 *
 * <p>The learner-facing types have no field for whether an option is correct. That is deliberate
 * and structural: there is no flag to forget to strip, because the shape cannot express it.
 * Correctness only exists on {@link MarkedQuestion}, which is only ever returned in response to a
 * submitted attempt.
 */
public final class QuizResponses {

  private QuizResponses() {}

  /** An option as a clinician sees it before answering. */
  public record QuizOptionResponse(UUID optionId, int position, String label) {}

  /** A question as a clinician sees it before answering. No answer, no explanation. */
  public record QuizQuestionResponse(
      UUID questionId, int position, String prompt, List<QuizOptionResponse> options) {}

  public record QuizResponse(UUID versionId, boolean passed, int attemptCount, List<QuizQuestionResponse> questions) {}

  /** An option as an author sees it, where the answer is the point. */
  public record AuthoredOptionResponse(UUID optionId, int position, String label, boolean correct) {}

  public record AuthoredQuestionResponse(
      UUID questionId,
      int position,
      String prompt,
      String explanation,
      List<AuthoredOptionResponse> options) {}

  /** One question after marking: what they chose, what was right, and why. */
  public record MarkedQuestion(
      UUID questionId,
      String prompt,
      UUID chosenOptionId,
      UUID correctOptionId,
      boolean wasCorrect,
      String explanation) {}

  public record AttemptResultResponse(
      int attemptNumber,
      int correctCount,
      int questionCount,
      boolean passed,
      List<MarkedQuestion> questions) {}
}
