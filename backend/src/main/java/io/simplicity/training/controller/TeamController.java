package io.simplicity.training.controller;

import io.simplicity.training.model.request.AddTeamMemberRequest;
import io.simplicity.training.model.request.CreateTeamRequest;
import io.simplicity.training.model.request.UpdateTeamRequest;
import io.simplicity.training.model.response.TeamMemberDetailResponse;
import io.simplicity.training.model.response.TeamResponse;
import io.simplicity.training.security.CurrentPrincipal;
import io.simplicity.training.service.TeamService;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/orgs/{orgId}/teams")
@RequiredArgsConstructor
@Tag(name = "Teams")
public class TeamController {

  private final TeamService teamService;

  @GetMapping
  @PreAuthorize("@authz.isOrgMember(#orgId)")
  @Operation(operationId = "listTeams", summary = "List the teams in an organisation")
  public List<TeamResponse> list(@PathVariable UUID orgId) {
    return teamService.list(orgId);
  }

  @PostMapping
  @PreAuthorize("@authz.isOrgAdmin(#orgId)")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
      operationId = "createTeam",
      summary = "Create a team",
      description = "Restricted to organisation administrators; team administrators cannot.")
  public TeamResponse create(@PathVariable UUID orgId, @Valid @RequestBody CreateTeamRequest request) {
    return teamService.create(CurrentPrincipal.require(), orgId, request);
  }

  @GetMapping("/{teamId}")
  @PreAuthorize("@authz.isOrgMember(#orgId)")
  @Operation(operationId = "getTeam", summary = "Fetch one team")
  public TeamResponse get(@PathVariable UUID orgId, @PathVariable UUID teamId) {
    return teamService.get(orgId, teamId);
  }

  @PatchMapping("/{teamId}")
  @PreAuthorize("@authz.canManageTeam(#orgId, #teamId)")
  @Operation(operationId = "updateTeam", summary = "Rename or redescribe a team")
  public TeamResponse update(
      @PathVariable UUID orgId,
      @PathVariable UUID teamId,
      @Valid @RequestBody UpdateTeamRequest request) {
    return teamService.update(CurrentPrincipal.require(), orgId, teamId, request);
  }

  @DeleteMapping("/{teamId}")
  @PreAuthorize("@authz.isOrgAdmin(#orgId)")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(
      operationId = "deleteTeam",
      summary = "Delete a team",
      description =
          "Organisation administrators only. A team administrator can manage their team's "
              + "membership but cannot remove the team itself.")
  public void delete(@PathVariable UUID orgId, @PathVariable UUID teamId) {
    teamService.delete(CurrentPrincipal.require(), orgId, teamId);
  }

  @GetMapping("/{teamId}/members")
  @PreAuthorize("@authz.isOrgMember(#orgId)")
  @Operation(operationId = "listTeamMembers", summary = "List the people in a team")
  public List<TeamMemberDetailResponse> listMembers(
      @PathVariable UUID orgId, @PathVariable UUID teamId) {
    return teamService.listMembers(orgId, teamId);
  }

  @PostMapping("/{teamId}/members")
  @PreAuthorize("@authz.canManageTeam(#orgId, #teamId)")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(operationId = "addTeamMember", summary = "Add an organisation member to a team")
  public TeamMemberDetailResponse addMember(
      @PathVariable UUID orgId,
      @PathVariable UUID teamId,
      @Valid @RequestBody AddTeamMemberRequest request) {
    return teamService.addMember(
        CurrentPrincipal.require(), orgId, teamId, request.userId(), request.teamRole());
  }

  @DeleteMapping("/{teamId}/members/{userId}")
  @PreAuthorize("@authz.canManageTeam(#orgId, #teamId)")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(operationId = "removeTeamMember", summary = "Remove somebody from a team")
  public void removeMember(
      @PathVariable UUID orgId, @PathVariable UUID teamId, @PathVariable UUID userId) {
    teamService.removeMember(CurrentPrincipal.require(), orgId, teamId, userId);
  }
}
