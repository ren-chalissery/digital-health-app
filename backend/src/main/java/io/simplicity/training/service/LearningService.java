package io.simplicity.training.service;

import io.simplicity.training.exception.ForbiddenException;
import io.simplicity.training.exception.NotFoundException;
import io.simplicity.training.model.entity.ModuleSection;
import io.simplicity.training.model.entity.ModuleVersion;
import io.simplicity.training.model.entity.Team;
import io.simplicity.training.model.entity.TrainingModule;
import io.simplicity.training.model.entity.UserModuleCompletion;
import io.simplicity.training.model.entity.UserSectionProgress;
import io.simplicity.training.model.enums.LearningStatus;
import io.simplicity.training.model.enums.ModuleStatus;
import io.simplicity.training.model.response.ModuleResponses.AssignedModuleResponse;
import io.simplicity.training.model.response.ModuleResponses.LearnerModuleResponse;
import io.simplicity.training.model.response.ModuleResponses.SectionResponse;
import io.simplicity.training.repository.ModuleRepository;
import io.simplicity.training.repository.ModuleSectionRepository;
import io.simplicity.training.repository.ModuleVersionRepository;
import io.simplicity.training.repository.TeamModuleAssignmentRepository;
import io.simplicity.training.repository.TeamRepository;
import io.simplicity.training.repository.UserModuleCompletionRepository;
import io.simplicity.training.repository.UserSectionProgressRepository;
import io.simplicity.training.security.AppPrincipal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reading training content.
 *
 * <p>Membership of the organisation is not enough to see a module: it must be assigned to a team
 * the caller is in. That check lives here rather than in an annotation because it depends on the
 * module, and it is the reason an organisation member cannot read a colleague's training by id.
 */
@Service
@RequiredArgsConstructor
public class LearningService {

  private final ModuleRepository modules;
  private final ModuleVersionRepository versions;
  private final ModuleSectionRepository sections;
  private final TeamModuleAssignmentRepository assignments;
  private final TeamRepository teams;
  private final UserSectionProgressRepository sectionProgress;
  private final UserModuleCompletionRepository completions;
  private final QuizService quiz;

  @Transactional(readOnly = true)
  public List<AssignedModuleResponse> assigned(AppPrincipal principal, UUID orgId) {
    List<UUID> moduleIds = assignedModuleIds(principal, orgId);
    List<AssignedModuleResponse> result = new ArrayList<>();

    for (UUID moduleId : moduleIds) {
      TrainingModule module =
          modules.findByIdAndOrgIdAndArchivedAtIsNull(moduleId, orgId).orElse(null);
      if (module == null) {
        continue;
      }
      ModuleVersion current = publishedVersion(moduleId).orElse(null);
      if (current == null) {
        // A module nobody has published yet is not content, whatever it is assigned to.
        continue;
      }

      List<ModuleSection> body = sections.findByVersionIdOrderByPositionAsc(current.getId());
      int done = completedSectionIds(principal.userId(), body).size();
      result.add(
          new AssignedModuleResponse(
              module.getId(),
              module.getTitle(),
              module.getSummary(),
              current.getId(),
              body.size(),
              done,
              quiz.hasQuestions(current.getId()),
              quiz.hasPassed(principal.userId(), current.getId()),
              statusOf(principal.userId(), moduleId, current, body.size(), done)));
    }
    return result;
  }

  @Transactional(readOnly = true)
  public LearnerModuleResponse read(AppPrincipal principal, UUID orgId, UUID moduleId) {
    requireAssigned(principal, orgId, moduleId);
    TrainingModule module =
        modules
            .findByIdAndOrgIdAndArchivedAtIsNull(moduleId, orgId)
            .orElseThrow(() -> NotFoundException.of("Module", moduleId));
    ModuleVersion current =
        publishedVersion(moduleId)
            .orElseThrow(() -> NotFoundException.of("Published version of module", moduleId));

    List<ModuleSection> body = sections.findByVersionIdOrderByPositionAsc(current.getId());
    List<UUID> done = completedSectionIds(principal.userId(), body);

    return new LearnerModuleResponse(
        module.getId(),
        module.getTitle(),
        module.getSummary(),
        current.getId(),
        statusOf(principal.userId(), moduleId, current, body.size(), done.size()),
        body.stream()
            .map(s -> new SectionResponse(s.getId(), s.getPosition(), s.getTitle(), s.getBody()))
            .toList(),
        done,
        quiz.hasQuestions(current.getId()),
        quiz.hasPassed(principal.userId(), current.getId()));
  }

  /**
   * Records one section as read, and completes the module in the same transaction when it was the
   * last one. Deriving completion here rather than trusting a separate call means a client cannot
   * leave somebody with every section read and the module unfinished.
   */
  @Transactional
  public LearnerModuleResponse completeSection(
      AppPrincipal principal, UUID orgId, UUID sectionId) {
    ModuleSection section =
        sections.findById(sectionId).orElseThrow(() -> NotFoundException.of("Section", sectionId));
    ModuleVersion version =
        versions
            .findById(section.getVersionId())
            .orElseThrow(() -> NotFoundException.of("Module version", section.getVersionId()));

    requireAssigned(principal, orgId, version.getModuleId());
    if (version.getStatus() != ModuleStatus.PUBLISHED) {
      throw new ForbiddenException("That section belongs to a version that is not published");
    }

    sectionProgress.save(UserSectionProgress.of(principal.userId(), sectionId));
    sectionProgress.flush();

    recordCompletionIfFinished(principal.userId(), version.getId());
    return read(principal, orgId, version.getModuleId());
  }

  /**
   * Every section read, and the quiz passed if there is one. Called from both the section and the
   * quiz paths, because either can be the last thing a clinician does.
   */
  public void recordCompletionIfFinished(UUID userId, UUID versionId) {
    List<ModuleSection> body = sections.findByVersionIdOrderByPositionAsc(versionId);
    boolean readEverything = completedSectionIds(userId, body).size() == body.size();
    boolean quizSettled = !quiz.hasQuestions(versionId) || quiz.hasPassed(userId, versionId);

    if (readEverything && quizSettled) {
      completions.save(UserModuleCompletion.of(userId, versionId));
    }
  }

  // -----------------------------------------------------------------------------------------

  /**
   * Completed when this exact version is completed. When an earlier version was completed and the
   * current one was published as substantive, it needs redoing; when it was published as a
   * correction, the earlier completion still counts.
   */
  private LearningStatus statusOf(
      UUID userId, UUID moduleId, ModuleVersion current, int sectionCount, int completedCount) {
    List<UUID> allVersionIds =
        versions.findByModuleIdOrderByVersionNumberDesc(moduleId).stream()
            .map(ModuleVersion::getId)
            .toList();
    Set<UUID> completedVersions =
        new HashSet<>(completions.findCompletedVersionIds(userId, allVersionIds));

    if (completedVersions.contains(current.getId())) {
      return LearningStatus.COMPLETED;
    }
    if (!completedVersions.isEmpty()) {
      return current.isSupersedesCompletions()
          ? LearningStatus.NEEDS_REDOING
          : LearningStatus.COMPLETED;
    }
    // Anything begun is in progress, including every section read with the quiz still to pass.
    // Comparing against the section count instead would report that as not started.
    return completedCount > 0 ? LearningStatus.IN_PROGRESS : LearningStatus.NOT_STARTED;
  }

  /**
   * The version a clinician is entitled to work against, having checked the module is assigned to
   * one of their teams. The quiz endpoints go through here so that the assignment rule lives in one
   * place rather than being restated wherever a version is needed.
   */
  @Transactional(readOnly = true)
  public UUID publishedVersionFor(AppPrincipal principal, UUID orgId, UUID moduleId) {
    requireAssigned(principal, orgId, moduleId);
    modules
        .findByIdAndOrgIdAndArchivedAtIsNull(moduleId, orgId)
        .orElseThrow(() -> NotFoundException.of("Module", moduleId));
    return publishedVersion(moduleId)
        .orElseThrow(() -> NotFoundException.of("Published version of module", moduleId))
        .getId();
  }

  private List<UUID> completedSectionIds(UUID userId, List<ModuleSection> body) {
    if (body.isEmpty()) {
      return List.of();
    }
    return sectionProgress.findCompletedSectionIds(
        userId, body.stream().map(ModuleSection::getId).toList());
  }

  private java.util.Optional<ModuleVersion> publishedVersion(UUID moduleId) {
    return versions.findFirstByModuleIdAndStatusOrderByVersionNumberDesc(
        moduleId, ModuleStatus.PUBLISHED);
  }

  private List<UUID> assignedModuleIds(AppPrincipal principal, UUID orgId) {
    List<UUID> teamIds =
        teams.findByOrgIdOrderByNameAsc(orgId).stream()
            .map(Team::getId)
            .filter(principal::isMemberOfTeam)
            .toList();
    return teamIds.isEmpty() ? List.of() : assignments.findModuleIdsForTeams(teamIds);
  }

  private void requireAssigned(AppPrincipal principal, UUID orgId, UUID moduleId) {
    if (!assignedModuleIds(principal, orgId).contains(moduleId)) {
      throw new ForbiddenException("That module is not assigned to any of your teams");
    }
  }
}
