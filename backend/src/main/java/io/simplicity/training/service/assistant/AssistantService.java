package io.simplicity.training.service.assistant;

import io.simplicity.training.exception.ConflictException;
import io.simplicity.training.model.response.AssistantResponses.AnswerResponse;
import io.simplicity.training.model.response.AssistantResponses.CitationResponse;
import io.simplicity.training.repository.ModuleChunkRepository;
import io.simplicity.training.security.AppPrincipal;
import io.simplicity.training.service.LearningService;
import io.simplicity.training.service.RateLimiter;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Answers questions from an organisation's training content, or declines to.
 *
 * <p>Nothing here can reach a reflection. That is deliberate and worth keeping true: the feature
 * somebody will eventually ask for — help me reflect on this — is precisely the one that would
 * break what Phase 3 promised.
 */
@Service
@RequiredArgsConstructor
public class AssistantService {

  private static final int PASSAGES = 6;

  /**
   * Cosine distance beyond which the training is treated as not covering the question. Retrieval,
   * not the prompt, is what stops a clinical question being answered from the model's general
   * knowledge: a prompt is a request, and this is a gate.
   */
  private static final double RELEVANCE_THRESHOLD = 0.55;

  private static final int QUESTIONS_PER_HOUR = 30;

  private static final String NOT_COVERED =
      "The training material does not cover that. If it is about a particular person or a "
          + "situation you are managing, your supervisor is the right place to take it.";

  private final ModuleChunkRepository chunks;
  private final Embedder embedder;
  private final AnswerGenerator generator;
  private final LearningService learning;
  private final RateLimiter rateLimiter;

  @Transactional(readOnly = true)
  public AnswerResponse ask(AppPrincipal principal, UUID orgId, String question) {
    if (!rateLimiter.tryAcquire(
        "assistant", principal.userId().toString(), QUESTIONS_PER_HOUR, Duration.ofHours(1))) {
      throw new ConflictException(
          "You have asked a lot of questions in the last hour. Try again shortly.");
    }

    List<Object[]> nearest =
        chunks.findNearest(orgId, Vectors.toLiteral(embedder.embed(question)), PASSAGES);

    if (nearest.isEmpty() || distanceOf(nearest.get(0)) > RELEVANCE_THRESHOLD) {
      // No model call at all, so a question the training cannot answer costs nothing.
      return new AnswerResponse(false, NOT_COVERED, List.of());
    }

    List<Object[]> relevant =
        nearest.stream().filter(row -> distanceOf(row) <= RELEVANCE_THRESHOLD).toList();

    String answer =
        generator.answer(
            question,
            relevant.stream()
                .map(
                    row ->
                        new AnswerGenerator.Passage(
                            (String) row[2], (String) row[3], (String) row[4]))
                .toList());

    return new AnswerResponse(true, answer, citations(principal, orgId, relevant));
  }

  /**
   * One citation per module, not per passage: three passages from the same module is one source as
   * far as a reader is concerned.
   *
   * <p>Each carries whether that module is assigned to the caller. Retrieval spans the whole
   * organisation, so the assistant can cite something they cannot open in Learn, and the client
   * links only the ones they can actually reach.
   */
  private List<CitationResponse> citations(
      AppPrincipal principal, UUID orgId, List<Object[]> rows) {
    Map<UUID, CitationResponse> byModule = new LinkedHashMap<>();
    List<UUID> assigned = learning.assignedModuleIdsFor(principal, orgId);

    for (Object[] row : rows) {
      UUID moduleId = (UUID) row[1];
      byModule.computeIfAbsent(
          moduleId,
          id ->
              new CitationResponse(
                  id, (String) row[2], (String) row[3], assigned.contains(id)));
    }
    return new ArrayList<>(byModule.values());
  }

  private double distanceOf(Object[] row) {
    Object distance = row[5];
    return distance instanceof BigDecimal decimal
        ? decimal.doubleValue()
        : ((Number) distance).doubleValue();
  }
}
