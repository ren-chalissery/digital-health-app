package io.simplicity.training.model.response;

import io.simplicity.training.model.enums.LearningStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Everything the authoring and learning screens read. */
public final class ModuleResponses {

  private ModuleResponses() {}

  /** One section as an author sees it, and as a learner reads it. */
  public record SectionResponse(UUID sectionId, int position, String title, String body) {}

  /** A version with its sections, for the authoring screen. */
  public record VersionResponse(
      UUID versionId,
      int versionNumber,
      String status,
      boolean supersedesCompletions,
      Instant publishedAt,
      List<SectionResponse> sections) {}

  /**
   * A module as its author sees it: both the published version learners currently have and the
   * draft being worked on, so the editor can show one beside the other.
   */
  public record AuthoredModuleResponse(
      UUID moduleId,
      String title,
      String summary,
      Instant createdAt,
      VersionResponse published,
      VersionResponse draft,
      List<UUID> assignedTeamIds) {}

  /** A row in the authoring list. */
  public record ModuleSummaryResponse(
      UUID moduleId,
      String title,
      String summary,
      Integer publishedVersion,
      boolean hasDraft,
      int assignedTeamCount) {}

  /** A row in Learn, and the source of the Dashboard's counts. */
  public record AssignedModuleResponse(
      UUID moduleId,
      String title,
      String summary,
      UUID versionId,
      int sectionCount,
      int completedSectionCount,
      LearningStatus status) {}

  /** A module opened in Learn: the published version, its sections, and what is already done. */
  public record LearnerModuleResponse(
      UUID moduleId,
      String title,
      String summary,
      UUID versionId,
      LearningStatus status,
      List<SectionResponse> sections,
      List<UUID> completedSectionIds) {}
}
