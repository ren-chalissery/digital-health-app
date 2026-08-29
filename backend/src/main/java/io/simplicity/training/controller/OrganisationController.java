package io.simplicity.training.controller;

import io.simplicity.training.model.enums.OrgRole;
import io.simplicity.training.model.request.ChangeOrgRoleRequest;
import io.simplicity.training.model.request.CreateOrganisationRequest;
import io.simplicity.training.model.response.OrgMemberResponse;
import io.simplicity.training.model.response.OrganisationResponse;
import io.simplicity.training.security.CurrentPrincipal;
import io.simplicity.training.service.OrganisationService;
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

/**
 * Organisation-scoped routes carry {@code orgId} in the path so the tenant boundary is explicit in
 * the URL and can be checked in one declarative place.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Organisations")
public class OrganisationController {

  private final OrganisationService organisationService;

  @PostMapping("/api/v1/organisations")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
      operationId = "createOrganisation",
      summary = "Create an organisation",
      description = "The caller becomes its first administrator. Used by the self-signup flow.")
  public OrganisationResponse create(@Valid @RequestBody CreateOrganisationRequest request) {
    return organisationService.create(CurrentPrincipal.require(), request);
  }

  @GetMapping("/api/v1/orgs/{orgId}")
  @PreAuthorize("@authz.isOrgMember(#orgId)")
  @Operation(operationId = "getOrganisation", summary = "Fetch one organisation")
  public OrganisationResponse get(@PathVariable UUID orgId) {
    return organisationService.get(orgId);
  }

  @GetMapping("/api/v1/orgs/{orgId}/members")
  @PreAuthorize("@authz.isOrgMember(#orgId)")
  @Operation(operationId = "listOrganisationMembers", summary = "List everybody in an organisation")
  public List<OrgMemberResponse> listMembers(@PathVariable UUID orgId) {
    return organisationService.listMembers(orgId);
  }

  @PatchMapping("/api/v1/orgs/{orgId}/members/{userId}")
  @PreAuthorize("@authz.isOrgAdmin(#orgId)")
  @Operation(
      operationId = "changeOrganisationRole",
      summary = "Change a member's organisation role")
  public OrgMemberResponse changeRole(
      @PathVariable UUID orgId,
      @PathVariable UUID userId,
      @Valid @RequestBody ChangeOrgRoleRequest request) {
    OrgRole role = request.orgRole();
    return organisationService.changeRole(CurrentPrincipal.require(), orgId, userId, role);
  }

  // Declared before the {userId} route so that "me" is never mistaken for an identifier.
  @DeleteMapping("/api/v1/orgs/{orgId}/members/me")
  @PreAuthorize("@authz.isOrgMember(#orgId)")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(
      operationId = "leaveOrganisation",
      summary = "Leave an organisation",
      description =
          "Ends the caller's own membership and their teams within it. The last administrator may "
              + "leave, which archives the organisation rather than leaving nobody able to "
              + "administer it.")
  public void leave(@PathVariable UUID orgId) {
    organisationService.leave(CurrentPrincipal.require(), orgId);
  }

  @DeleteMapping("/api/v1/orgs/{orgId}/members/{userId}")
  @PreAuthorize("@authz.isOrgAdmin(#orgId)")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(
      operationId = "removeOrganisationMember",
      summary = "Remove a member, ending their team memberships in this organisation")
  public void removeMember(@PathVariable UUID orgId, @PathVariable UUID userId) {
    organisationService.removeMember(CurrentPrincipal.require(), orgId, userId);
  }

  @DeleteMapping("/api/v1/orgs/{orgId}")
  @PreAuthorize("@authz.isOrgAdmin(#orgId)")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(
      operationId = "archiveOrganisation",
      summary = "Archive an organisation",
      description =
          "Makes it unreachable for every member while keeping its memberships, teams, and audit "
              + "history. Nothing is deleted.")
  public void archive(@PathVariable UUID orgId) {
    organisationService.archive(CurrentPrincipal.require(), orgId);
  }
}
