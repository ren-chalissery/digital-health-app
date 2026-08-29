package io.simplicity.training.controller;

import io.simplicity.training.model.request.QuizRequests.SubmitAttemptRequest;
import io.simplicity.training.model.response.ModuleResponses.AssignedModuleResponse;
import io.simplicity.training.model.response.ModuleResponses.LearnerModuleResponse;
import io.simplicity.training.model.response.QuizResponses.AttemptResultResponse;
import io.simplicity.training.model.response.MediaResponses.PlaybackResponse;
import io.simplicity.training.model.response.QuizResponses.QuizResponse;
import io.simplicity.training.security.AppPrincipal;
import io.simplicity.training.security.CurrentPrincipal;
import io.simplicity.training.service.LearningService;
import io.simplicity.training.service.QuizService;
import io.simplicity.training.service.media.MediaService;
import java.time.Duration;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Working through training content.
 *
 * <p>Organisation membership gets a caller through the annotation; whether a particular module is
 * assigned to one of their teams is settled inside the service, because it depends on the module.
 */
@RestController
@RequestMapping("/api/v1/orgs/{orgId}/learning")
@PreAuthorize("@authz.isOrgMember(#orgId)")
@Tag(name = "Learning", description = "Modules assigned to the signed-in clinician")
public class LearningController {

  private final LearningService learning;
  private final QuizService quizzes;
  private final MediaService media;
  private final Duration playbackTtl;

  public LearningController(
      LearningService learning,
      QuizService quizzes,
      MediaService media,
      io.simplicity.training.config.AppProperties properties) {
    this.learning = learning;
    this.quizzes = quizzes;
    this.media = media;
    this.playbackTtl = properties.media().playbackUrlTtl();
  }

  @GetMapping
  @Operation(
      operationId = "listAssignedModules",
      summary = "Modules assigned to the caller's teams, with their progress")
  public List<AssignedModuleResponse> assigned(@PathVariable UUID orgId) {
    return learning.assigned(CurrentPrincipal.require(), orgId);
  }

  @GetMapping("/{moduleId}")
  @Operation(operationId = "readModule", summary = "The published version of one assigned module")
  public LearnerModuleResponse read(@PathVariable UUID orgId, @PathVariable UUID moduleId) {
    return learning.read(CurrentPrincipal.require(), orgId, moduleId);
  }

  @GetMapping("/{moduleId}/quiz")
  @Operation(
      operationId = "getQuiz",
      summary = "The quiz for an assigned module",
      description = "Questions and options only. Which option is correct is never sent here.")
  public QuizResponse quiz(@PathVariable UUID orgId, @PathVariable UUID moduleId) {
    AppPrincipal principal = CurrentPrincipal.require();
    UUID versionId = learning.publishedVersionFor(principal, orgId, moduleId);
    return quizzes.describeForLearner(principal.userId(), versionId);
  }

  @PostMapping("/{moduleId}/quiz/attempts")
  @Operation(
      operationId = "submitQuizAttempt",
      summary = "Answer the quiz",
      description =
          "Marked on the server. Returns which questions were right, the correct answer, and the "
              + "author's explanation. Passing completes the module if every section is read.")
  public AttemptResultResponse submitAttempt(
      @PathVariable UUID orgId,
      @PathVariable UUID moduleId,
      @Valid @RequestBody SubmitAttemptRequest request) {
    AppPrincipal principal = CurrentPrincipal.require();
    UUID versionId = learning.publishedVersionFor(principal, orgId, moduleId);
    AttemptResultResponse result = quizzes.submit(principal.userId(), versionId, request);
    if (result.passed()) {
      learning.recordCompletionIfFinished(principal.userId(), versionId);
    }
    return result;
  }

  @GetMapping("/media/{assetId}/playback")
  @Operation(
      operationId = "getPlaybackUrl",
      summary = "A short-lived URL for a video in an assigned module",
      description =
          "Minted per request after the same assignment check that guards the module. Holding an "
              + "asset id is not authorisation.")
  public PlaybackResponse playback(@PathVariable UUID orgId, @PathVariable UUID assetId) {
    AppPrincipal principal = CurrentPrincipal.require();
    String url =
        media.playbackUrl(orgId, assetId, versionId -> learning.mayReachVersion(principal, orgId, versionId));
    return new PlaybackResponse(url, (int) playbackTtl.toSeconds());
  }

  @PutMapping("/sections/{sectionId}/complete")
  @Operation(
      operationId = "completeSection",
      summary = "Mark a section as read",
      description = "Completing the last section completes the module in the same transaction.")
  public LearnerModuleResponse completeSection(
      @PathVariable UUID orgId, @PathVariable UUID sectionId) {
    return learning.completeSection(CurrentPrincipal.require(), orgId, sectionId);
  }
}
