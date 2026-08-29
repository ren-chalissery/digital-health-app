package io.simplicity.training.service;

import io.simplicity.training.exception.ConflictException;
import io.simplicity.training.exception.NotFoundException;
import io.simplicity.training.model.entity.ModuleSection;
import io.simplicity.training.model.entity.ModuleVersion;
import io.simplicity.training.model.entity.Team;
import io.simplicity.training.model.entity.TeamModuleAssignment;
import io.simplicity.training.model.entity.TrainingModule;
import io.simplicity.training.model.enums.ModuleStatus;
import io.simplicity.training.model.request.ModuleRequests.CreateModuleRequest;
import io.simplicity.training.model.request.ModuleRequests.PublishRequest;
import io.simplicity.training.model.request.ModuleRequests.ReplaceSectionsRequest;
import io.simplicity.training.model.request.ModuleRequests.SectionInput;
import io.simplicity.training.model.request.ModuleRequests.UpdateModuleRequest;
import io.simplicity.training.model.response.ModuleResponses.AuthoredModuleResponse;
import io.simplicity.training.model.response.ModuleResponses.ModuleSummaryResponse;
import io.simplicity.training.model.response.ModuleResponses.SectionResponse;
import io.simplicity.training.model.response.ModuleResponses.VersionResponse;
import io.simplicity.training.repository.ModuleRepository;
import io.simplicity.training.repository.ModuleSectionRepository;
import io.simplicity.training.repository.ModuleVersionRepository;
import io.simplicity.training.repository.TeamModuleAssignmentRepository;
import io.simplicity.training.repository.TeamRepository;
import io.simplicity.training.security.AppPrincipal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Writing training content. Everything here is an organisation administrator's work. */
@Service
@RequiredArgsConstructor
public class ModuleAuthoringService {

  private final ModuleRepository modules;
  private final ModuleVersionRepository versions;
  private final ModuleSectionRepository sections;
  private final TeamModuleAssignmentRepository assignments;
  private final TeamRepository teams;
  private final QuizService quizzes;
  private final AuditService audit;

  @Transactional
  public AuthoredModuleResponse create(AppPrincipal actor, UUID orgId, CreateModuleRequest request) {
    TrainingModule module =
        modules.save(
            TrainingModule.builder()
                .orgId(orgId)
                .title(request.title().trim())
                .summary(trimmed(request.summary()))
                .createdBy(actor.userId())
                .build());

    // A module with no version cannot be edited, so the first draft comes with it.
    versions.save(
        ModuleVersion.builder()
            .moduleId(module.getId())
            .versionNumber(1)
            .status(ModuleStatus.DRAFT)
            .build());

    audit.record(actor.userId(), orgId, "MODULE_CREATED", "module", module.getId());
    return describe(module);
  }

  @Transactional(readOnly = true)
  public List<ModuleSummaryResponse> list(UUID orgId) {
    List<TrainingModule> all = modules.findByOrgIdAndArchivedAtIsNullOrderByTitleAsc(orgId);
    if (all.isEmpty()) {
      return List.of();
    }
    List<UUID> ids = all.stream().map(TrainingModule::getId).toList();
    List<ModuleVersion> allVersions = versions.findByModuleIdIn(ids);
    List<TeamModuleAssignment> allAssignments = assignments.findByModuleIdIn(ids);

    return all.stream()
        .map(
            module -> {
              Integer published =
                  allVersions.stream()
                      .filter(v -> v.getModuleId().equals(module.getId()))
                      .filter(v -> v.getStatus() == ModuleStatus.PUBLISHED)
                      .map(ModuleVersion::getVersionNumber)
                      .max(Integer::compareTo)
                      .orElse(null);
              boolean hasDraft =
                  allVersions.stream()
                      .anyMatch(
                          v ->
                              v.getModuleId().equals(module.getId())
                                  && v.getStatus() == ModuleStatus.DRAFT);
              int assigned =
                  (int)
                      allAssignments.stream()
                          .filter(a -> a.getModuleId().equals(module.getId()))
                          .count();
              return new ModuleSummaryResponse(
                  module.getId(), module.getTitle(), module.getSummary(), published, hasDraft, assigned);
            })
        .toList();
  }

  @Transactional(readOnly = true)
  public AuthoredModuleResponse get(UUID orgId, UUID moduleId) {
    return describe(require(orgId, moduleId));
  }

  @Transactional
  public AuthoredModuleResponse update(
      AppPrincipal actor, UUID orgId, UUID moduleId, UpdateModuleRequest request) {
    TrainingModule module = require(orgId, moduleId);
    module.setTitle(request.title().trim());
    module.setSummary(trimmed(request.summary()));
    return describe(modules.save(module));
  }

  @Transactional
  public void archive(AppPrincipal actor, UUID orgId, UUID moduleId) {
    TrainingModule module = require(orgId, moduleId);
    module.setArchivedAt(Instant.now());
    modules.save(module);
    audit.record(actor.userId(), orgId, "MODULE_ARCHIVED", "module", moduleId);
  }

  /** Opens a new draft, seeded with whatever learners currently have so an edit starts from it. */
  @Transactional
  public AuthoredModuleResponse openDraft(UUID orgId, UUID moduleId) {
    TrainingModule module = require(orgId, moduleId);
    if (versions.findByModuleIdAndStatus(moduleId, ModuleStatus.DRAFT).isPresent()) {
      throw new ConflictException("This module already has a draft");
    }

    ModuleVersion latest =
        versions.findByModuleIdOrderByVersionNumberDesc(moduleId).stream()
            .findFirst()
            .orElseThrow(() -> NotFoundException.of("Module version for module", moduleId));

    ModuleVersion draft =
        versions.save(
            ModuleVersion.builder()
                .moduleId(moduleId)
                .versionNumber(latest.getVersionNumber() + 1)
                .status(ModuleStatus.DRAFT)
                .build());

    for (ModuleSection existing : sections.findByVersionIdOrderByPositionAsc(latest.getId())) {
      sections.save(
          ModuleSection.builder()
              .versionId(draft.getId())
              .position(existing.getPosition())
              .title(existing.getTitle())
              .body(existing.getBody())
              .build());
    }
    return describe(module);
  }

  @Transactional
  public AuthoredModuleResponse replaceSections(
      UUID orgId, UUID moduleId, ReplaceSectionsRequest request) {
    TrainingModule module = require(orgId, moduleId);
    ModuleVersion draft = requireDraft(moduleId);

    // Positions are assigned from the order given rather than trusted from the client, so a
    // reorder that also renames and deletes cannot leave a gap or a duplicate.
    sections.deleteByVersionId(draft.getId());
    sections.flush();

    int position = 0;
    for (SectionInput input : request.sections()) {
      sections.save(
          ModuleSection.builder()
              .versionId(draft.getId())
              .position(position++)
              .title(input.title().trim())
              .body(input.body() == null ? "" : input.body())
              .build());
    }
    return describe(module);
  }

  /** Replaces the draft's questions, as sections are replaced: whole, from one screen. */
  @Transactional
  public AuthoredModuleResponse replaceQuiz(
      UUID orgId, UUID moduleId, List<io.simplicity.training.model.request.QuizRequests.QuestionInput> questions) {
    TrainingModule module = require(orgId, moduleId);
    quizzes.replaceQuiz(requireDraft(moduleId).getId(), questions);
    return describe(module);
  }

  @Transactional
  public AuthoredModuleResponse publish(
      AppPrincipal actor, UUID orgId, UUID moduleId, PublishRequest request) {
    TrainingModule module = require(orgId, moduleId);
    ModuleVersion draft = requireDraft(moduleId);

    if (sections.countByVersionId(draft.getId()) == 0) {
      throw new ConflictException("A module needs at least one section before it can be published");
    }
    // A question with no correct answer would leave the module permanently uncompletable for
    // everybody it is assigned to. Catch it while the author is still here to fix it.
    quizzes.validateForPublishing(draft.getId());

    draft.setStatus(ModuleStatus.PUBLISHED);
    draft.setSupersedesCompletions(request.supersedesCompletions());
    draft.setPublishedAt(Instant.now());
    draft.setPublishedBy(actor.userId());
    versions.save(draft);

    audit.record(actor.userId(), orgId, "MODULE_PUBLISHED", "module", moduleId);
    return describe(module);
  }

  @Transactional
  public AuthoredModuleResponse assignTeams(
      AppPrincipal actor, UUID orgId, UUID moduleId, List<UUID> teamIds) {
    TrainingModule module = require(orgId, moduleId);

    // A team from another organisation would hand its members content they have no membership of.
    List<UUID> withinOrg =
        teams.findAllById(teamIds).stream()
            .filter(team -> team.getOrgId().equals(orgId))
            .map(Team::getId)
            .toList();
    if (withinOrg.size() != teamIds.size()) {
      throw new NotFoundException("One or more teams do not belong to this organisation");
    }

    assignments.deleteAll(assignments.findByModuleId(moduleId));
    assignments.flush();
    for (UUID teamId : withinOrg) {
      assignments.save(TeamModuleAssignment.of(teamId, moduleId, actor.userId()));
    }

    audit.record(actor.userId(), orgId, "MODULE_ASSIGNED", "module", moduleId);
    return describe(module);
  }

  // -----------------------------------------------------------------------------------------

  private TrainingModule require(UUID orgId, UUID moduleId) {
    return modules
        .findByIdAndOrgIdAndArchivedAtIsNull(moduleId, orgId)
        .orElseThrow(() -> NotFoundException.of("Module", moduleId));
  }

  private ModuleVersion requireDraft(UUID moduleId) {
    return versions
        .findByModuleIdAndStatus(moduleId, ModuleStatus.DRAFT)
        .orElseThrow(() -> new ConflictException("This module has no draft to edit"));
  }

  private AuthoredModuleResponse describe(TrainingModule module) {
    List<ModuleVersion> all = versions.findByModuleIdOrderByVersionNumberDesc(module.getId());
    Optional<ModuleVersion> published =
        all.stream().filter(v -> v.getStatus() == ModuleStatus.PUBLISHED).findFirst();
    Optional<ModuleVersion> draft =
        all.stream().filter(v -> v.getStatus() == ModuleStatus.DRAFT).findFirst();

    return new AuthoredModuleResponse(
        module.getId(),
        module.getTitle(),
        module.getSummary(),
        module.getCreatedAt(),
        published.map(this::describeVersion).orElse(null),
        draft.map(this::describeVersion).orElse(null),
        assignments.findByModuleId(module.getId()).stream()
            .map(TeamModuleAssignment::getTeamId)
            .toList());
  }

  private VersionResponse describeVersion(ModuleVersion version) {
    List<SectionResponse> body = new ArrayList<>();
    for (ModuleSection section : sections.findByVersionIdOrderByPositionAsc(version.getId())) {
      body.add(
          new SectionResponse(
              section.getId(), section.getPosition(), section.getTitle(), section.getBody()));
    }
    return new VersionResponse(
        version.getId(),
        version.getVersionNumber(),
        version.getStatus().name(),
        version.isSupersedesCompletions(),
        version.getPublishedAt(),
        body,
        quizzes.describeForAuthor(version.getId()));
  }

  private String trimmed(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
