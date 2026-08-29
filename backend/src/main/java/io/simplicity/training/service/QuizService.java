package io.simplicity.training.service;

import io.simplicity.training.exception.ConflictException;
import io.simplicity.training.exception.NotFoundException;
import io.simplicity.training.model.entity.QuizAttempt;
import io.simplicity.training.model.entity.QuizOption;
import io.simplicity.training.model.entity.QuizQuestion;
import io.simplicity.training.model.request.QuizRequests.AnswerInput;
import io.simplicity.training.model.request.QuizRequests.OptionInput;
import io.simplicity.training.model.request.QuizRequests.QuestionInput;
import io.simplicity.training.model.request.QuizRequests.SubmitAttemptRequest;
import io.simplicity.training.model.response.QuizResponses.AttemptResultResponse;
import io.simplicity.training.model.response.QuizResponses.AuthoredOptionResponse;
import io.simplicity.training.model.response.QuizResponses.AuthoredQuestionResponse;
import io.simplicity.training.model.response.QuizResponses.MarkedQuestion;
import io.simplicity.training.model.response.QuizResponses.QuizOptionResponse;
import io.simplicity.training.model.response.QuizResponses.QuizQuestionResponse;
import io.simplicity.training.model.response.QuizResponses.QuizResponse;
import io.simplicity.training.repository.QuizAttemptRepository;
import io.simplicity.training.repository.QuizOptionRepository;
import io.simplicity.training.repository.QuizQuestionRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writing and taking quizzes.
 *
 * <p>Marking happens here and nowhere else. What a client submits is a set of chosen options; which
 * of them were right is decided against the database, so a client cannot mark itself.
 */
@Service
@RequiredArgsConstructor
public class QuizService {

  private final QuizQuestionRepository questions;
  private final QuizOptionRepository options;
  private final QuizAttemptRepository attempts;

  @Transactional
  public void replaceQuiz(UUID versionId, List<QuestionInput> input) {
    questions.deleteByVersionId(versionId);
    questions.flush();

    int questionPosition = 0;
    for (QuestionInput question : input) {
      QuizQuestion saved =
          questions.save(
              QuizQuestion.builder()
                  .versionId(versionId)
                  .position(questionPosition++)
                  .prompt(question.prompt().trim())
                  .explanation(blankToNull(question.explanation()))
                  .build());

      int optionPosition = 0;
      for (OptionInput option : question.options()) {
        options.save(
            QuizOption.builder()
                .questionId(saved.getId())
                .position(optionPosition++)
                .label(option.label().trim())
                .correct(option.correct())
                .build());
      }
    }
  }

  /**
   * Refuses a quiz that could never be passed. A question with no correct option would make the
   * module permanently uncompletable for everybody it is assigned to, and the moment to catch that
   * is while the author is still here.
   */
  @Transactional(readOnly = true)
  public void validateForPublishing(UUID versionId) {
    for (QuizQuestion question : questions.findByVersionIdOrderByPositionAsc(versionId)) {
      List<QuizOption> answers = options.findByQuestionIdOrderByPositionAsc(question.getId());
      if (answers.size() < 2) {
        throw new ConflictException(
            "\"" + question.getPrompt() + "\" needs at least two options");
      }
      long correct = answers.stream().filter(QuizOption::isCorrect).count();
      if (correct != 1) {
        throw new ConflictException(
            "\"" + question.getPrompt() + "\" needs exactly one correct option");
      }
    }
  }

  @Transactional(readOnly = true)
  public List<AuthoredQuestionResponse> describeForAuthor(UUID versionId) {
    List<AuthoredQuestionResponse> result = new ArrayList<>();
    for (QuizQuestion question : questions.findByVersionIdOrderByPositionAsc(versionId)) {
      result.add(
          new AuthoredQuestionResponse(
              question.getId(),
              question.getPosition(),
              question.getPrompt(),
              question.getExplanation(),
              options.findByQuestionIdOrderByPositionAsc(question.getId()).stream()
                  .map(
                      option ->
                          new AuthoredOptionResponse(
                              option.getId(), option.getPosition(), option.getLabel(), option.isCorrect()))
                  .toList()));
    }
    return result;
  }

  /** The learner's view. Built from a type with nowhere to put the answer. */
  @Transactional(readOnly = true)
  public QuizResponse describeForLearner(UUID userId, UUID versionId) {
    List<QuizQuestionResponse> asked = new ArrayList<>();
    for (QuizQuestion question : questions.findByVersionIdOrderByPositionAsc(versionId)) {
      asked.add(
          new QuizQuestionResponse(
              question.getId(),
              question.getPosition(),
              question.getPrompt(),
              options.findByQuestionIdOrderByPositionAsc(question.getId()).stream()
                  .map(
                      option ->
                          new QuizOptionResponse(option.getId(), option.getPosition(), option.getLabel()))
                  .toList()));
    }
    return new QuizResponse(
        versionId,
        attempts.existsByUserIdAndVersionIdAndPassedIsTrue(userId, versionId),
        attempts.findByUserIdAndVersionIdOrderByAttemptNumberAsc(userId, versionId).size(),
        asked);
  }

  @Transactional
  public AttemptResultResponse submit(UUID userId, UUID versionId, SubmitAttemptRequest request) {
    List<QuizQuestion> asked = questions.findByVersionIdOrderByPositionAsc(versionId);
    if (asked.isEmpty()) {
      throw NotFoundException.of("Quiz for module version", versionId);
    }

    Map<UUID, UUID> chosen = new HashMap<>();
    for (AnswerInput answer : request.answers()) {
      chosen.put(answer.questionId(), answer.optionId());
    }

    List<MarkedQuestion> marked = new ArrayList<>();
    int correctCount = 0;
    for (QuizQuestion question : asked) {
      UUID correctOptionId =
          options.findByQuestionIdOrderByPositionAsc(question.getId()).stream()
              .filter(QuizOption::isCorrect)
              .map(QuizOption::getId)
              .findFirst()
              .orElseThrow(
                  () ->
                      new ConflictException(
                          "This quiz has a question with no correct answer and cannot be taken"));

      UUID picked = chosen.get(question.getId());
      // An unanswered question is wrong rather than skipped: passing requires all of them, and
      // treating silence as neutral would let somebody pass by answering only what they knew.
      boolean wasCorrect = correctOptionId.equals(picked);
      if (wasCorrect) {
        correctCount++;
      }
      marked.add(
          new MarkedQuestion(
              question.getId(),
              question.getPrompt(),
              picked,
              correctOptionId,
              wasCorrect,
              question.getExplanation()));
    }

    boolean passed = correctCount == asked.size();
    int attemptNumber = attempts.highestAttemptNumber(userId, versionId) + 1;
    attempts.save(
        QuizAttempt.builder()
            .userId(userId)
            .versionId(versionId)
            .attemptNumber(attemptNumber)
            .correctCount(correctCount)
            .questionCount(asked.size())
            .passed(passed)
            .build());

    return new AttemptResultResponse(attemptNumber, correctCount, asked.size(), passed, marked);
  }

  @Transactional(readOnly = true)
  public boolean hasQuestions(UUID versionId) {
    return questions.countByVersionId(versionId) > 0;
  }

  @Transactional(readOnly = true)
  public boolean hasPassed(UUID userId, UUID versionId) {
    return attempts.existsByUserIdAndVersionIdAndPassedIsTrue(userId, versionId);
  }

  private String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
