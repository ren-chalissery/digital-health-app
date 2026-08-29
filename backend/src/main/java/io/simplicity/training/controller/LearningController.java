package io.simplicity.training.controller;

import io.simplicity.training.model.response.ModuleResponses.AssignedModuleResponse;
import io.simplicity.training.model.response.ModuleResponses.LearnerModuleResponse;
import io.simplicity.training.security.CurrentPrincipal;
import io.simplicity.training.service.LearningService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
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
@RequiredArgsConstructor
@PreAuthorize("@authz.isOrgMember(#orgId)")
@Tag(name = "Learning", description = "Modules assigned to the signed-in clinician")
public class LearningController {

  private final LearningService learning;

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
