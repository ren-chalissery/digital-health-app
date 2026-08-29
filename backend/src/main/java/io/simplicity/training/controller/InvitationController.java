package io.simplicity.training.controller;

import io.simplicity.training.model.request.CreateInvitationRequest;
import io.simplicity.training.model.response.InvitationPreviewResponse;
import io.simplicity.training.model.response.InvitationResponse;
import io.simplicity.training.security.CurrentPrincipal;
import io.simplicity.training.service.InvitationService;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "Invitations")
public class InvitationController {

  private final InvitationService invitationService;

  @GetMapping("/api/v1/orgs/{orgId}/invitations")
  @PreAuthorize("@authz.isOrgAdmin(#orgId)")
  public List<InvitationResponse> list(@PathVariable UUID orgId) {
    return invitationService.list(orgId);
  }

  @PostMapping("/api/v1/orgs/{orgId}/invitations")
  @PreAuthorize("@authz.isOrgAdmin(#orgId)")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
      summary = "Invite somebody to the organisation",
      description =
          "Re-inviting an address withdraws the outstanding invitation and issues a fresh link, "
              + "so only one token is ever live for a given address.")
  public InvitationResponse create(
      @PathVariable UUID orgId, @Valid @RequestBody CreateInvitationRequest request) {
    return invitationService.create(CurrentPrincipal.require(), orgId, request);
  }

  @DeleteMapping("/api/v1/orgs/{orgId}/invitations/{invitationId}")
  @PreAuthorize("@authz.isOrgAdmin(#orgId)")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void revoke(@PathVariable UUID orgId, @PathVariable UUID invitationId) {
    invitationService.revoke(CurrentPrincipal.require(), orgId, invitationId);
  }

  @GetMapping("/api/v1/invitations/{token}")
  @Operation(
      summary = "Preview an invitation before signing up",
      description =
          "Public. Returns valid=false for anything unknown, expired, or already used, so the "
              + "endpoint cannot be used to probe for live tokens.")
  public InvitationPreviewResponse preview(@PathVariable String token) {
    return invitationService.preview(token);
  }

  @PostMapping("/api/v1/invitations/{token}/accept")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Accept an invitation as the signed-in user")
  public void accept(@PathVariable String token) {
    invitationService.accept(CurrentPrincipal.require(), token);
  }
}
