package io.simplicity.training.controller;

import io.simplicity.training.model.request.ModuleRequests.AssignTeamsRequest;
import io.simplicity.training.model.request.ModuleRequests.CreateModuleRequest;
import io.simplicity.training.model.request.ModuleRequests.PublishRequest;
import io.simplicity.training.model.request.ModuleRequests.ReplaceSectionsRequest;
import io.simplicity.training.model.request.ModuleRequests.UpdateModuleRequest;
import io.simplicity.training.model.request.QuizRequests.ReplaceQuizRequest;
import io.simplicity.training.model.response.ModuleResponses.AuthoredModuleResponse;
import io.simplicity.training.model.response.ModuleResponses.ModuleSummaryResponse;
import io.simplicity.training.security.CurrentPrincipal;
import io.simplicity.training.service.ModuleAuthoringService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Authoring training content. Organisation administrators only, throughout. */
@RestController
@RequestMapping("/api/v1/orgs/{orgId}/modules")
@RequiredArgsConstructor
@PreAuthorize("@authz.isOrgAdmin(#orgId)")
@Tag(name = "Modules", description = "Authoring training modules")
public class ModuleController {

  private final ModuleAuthoringService authoring;

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
      operationId = "createModule",
      summary = "Create a module",
      description = "Comes with an empty first draft, since a module with no version cannot be edited.")
  public AuthoredModuleResponse create(
      @PathVariable UUID orgId, @Valid @RequestBody CreateModuleRequest request) {
    return authoring.create(CurrentPrincipal.require(), orgId, request);
  }

  @GetMapping
  @Operation(operationId = "listModules", summary = "List this organisation's modules")
  public List<ModuleSummaryResponse> list(@PathVariable UUID orgId) {
    return authoring.list(orgId);
  }

  @GetMapping("/{moduleId}")
  @Operation(
      operationId = "getModule",
      summary = "One module, with both the published version and the draft")
  public AuthoredModuleResponse get(@PathVariable UUID orgId, @PathVariable UUID moduleId) {
    return authoring.get(orgId, moduleId);
  }

  @PatchMapping("/{moduleId}")
  @Operation(operationId = "updateModule", summary = "Rename a module or change its summary")
  public AuthoredModuleResponse update(
      @PathVariable UUID orgId,
      @PathVariable UUID moduleId,
      @Valid @RequestBody UpdateModuleRequest request) {
    return authoring.update(CurrentPrincipal.require(), orgId, moduleId, request);
  }

  @DeleteMapping("/{moduleId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(
      operationId = "archiveModule",
      summary = "Archive a module",
      description = "Hidden from learners and authors alike. Completions and history are kept.")
  public void archive(@PathVariable UUID orgId, @PathVariable UUID moduleId) {
    authoring.archive(CurrentPrincipal.require(), orgId, moduleId);
  }

  @PostMapping("/{moduleId}/draft")
  @Operation(
      operationId = "openModuleDraft",
      summary = "Open a draft",
      description = "Copies what learners currently have, so an edit starts from the live content.")
  public AuthoredModuleResponse openDraft(@PathVariable UUID orgId, @PathVariable UUID moduleId) {
    return authoring.openDraft(orgId, moduleId);
  }

  @PutMapping("/{moduleId}/draft/sections")
  @Operation(
      operationId = "replaceModuleSections",
      summary = "Replace the draft's sections",
      description = "Sent whole: editing, reordering, and deleting all happen on one screen.")
  public AuthoredModuleResponse replaceSections(
      @PathVariable UUID orgId,
      @PathVariable UUID moduleId,
      @Valid @RequestBody ReplaceSectionsRequest request) {
    return authoring.replaceSections(orgId, moduleId, request);
  }

  @PutMapping("/{moduleId}/draft/quiz")
  @Operation(
      operationId = "replaceModuleQuiz",
      summary = "Replace the draft's quiz questions",
      description =
          "Each question needs at least two options and exactly one correct one; publishing "
              + "refuses anything else, since a question with no answer can never be passed.")
  public AuthoredModuleResponse replaceQuiz(
      @PathVariable UUID orgId,
      @PathVariable UUID moduleId,
      @Valid @RequestBody ReplaceQuizRequest request) {
    return authoring.replaceQuiz(orgId, moduleId, request.questions());
  }

  @PostMapping("/{moduleId}/draft/publish")
  @Operation(
      operationId = "publishModule",
      summary = "Publish the draft",
      description =
          "Set supersedesCompletions when the change is substantive, which sends anyone who "
              + "completed an earlier version back through it. A corrected typo should not.")
  public AuthoredModuleResponse publish(
      @PathVariable UUID orgId,
      @PathVariable UUID moduleId,
      @Valid @RequestBody PublishRequest request) {
    return authoring.publish(CurrentPrincipal.require(), orgId, moduleId, request);
  }

  @PutMapping("/{moduleId}/teams")
  @Operation(
      operationId = "assignModuleToTeams",
      summary = "Set which teams this module is assigned to")
  public AuthoredModuleResponse assignTeams(
      @PathVariable UUID orgId,
      @PathVariable UUID moduleId,
      @Valid @RequestBody AssignTeamsRequest request) {
    return authoring.assignTeams(CurrentPrincipal.require(), orgId, moduleId, request.teamIds());
  }
}
