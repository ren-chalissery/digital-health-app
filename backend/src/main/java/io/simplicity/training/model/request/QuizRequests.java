package io.simplicity.training.model.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/** What the quiz editor and the quiz itself send. */
public final class QuizRequests {

  private QuizRequests() {}

  public record OptionInput(@NotBlank @Size(max = 500) String label, boolean correct) {}

  public record QuestionInput(
      @NotBlank @Size(max = 1000) String prompt,
      @Size(max = 2000) String explanation,
      @NotNull @Size(min = 2, max = 10) List<@Valid OptionInput> options) {}

  /** The draft's questions in full, as sections are replaced. */
  public record ReplaceQuizRequest(@NotNull @Size(max = 100) List<@Valid QuestionInput> questions) {}

  public record AnswerInput(@NotNull UUID questionId, @NotNull UUID optionId) {}

  public record SubmitAttemptRequest(@NotNull @Size(max = 100) List<@Valid AnswerInput> answers) {}
}
