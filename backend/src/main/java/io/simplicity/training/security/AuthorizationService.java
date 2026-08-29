package io.simplicity.training.security;

import io.simplicity.training.repository.TeamRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Backs the {@code @PreAuthorize} expressions, registered as {@code @authz}.
 *
 * <p>Organisation and team roles are per-resource, so they cannot be expressed as Spring
 * authorities; every check takes the resource id and evaluates it against the caller's principal.
 *
 * <p>A failed check produces 403 whether or not the organisation exists. That is deliberate: a
 * distinct 404 for unknown organisations would let anyone probe which organisation ids are real.
 */
@Component("authz")
@RequiredArgsConstructor
public class AuthorizationService {

  private final TeamRepository teams;

  public boolean isOrgMember(UUID orgId) {
    AppPrincipal principal = principalOrNull();
    return principal != null && (principal.isSuperAdmin() || principal.isMemberOf(orgId));
  }

  public boolean isOrgAdmin(UUID orgId) {
    AppPrincipal principal = principalOrNull();
    return principal != null && (principal.isSuperAdmin() || principal.isAdminOf(orgId));
  }

  /**
   * Administering a team requires being an administrator of it <em>and</em> the team belonging to
   * the organisation in the path. Without the second condition, a team administrator in one
   * organisation could act on their team through another organisation's URL.
   */
  public boolean isTeamAdmin(UUID orgId, UUID teamId) {
    AppPrincipal principal = principalOrNull();
    if (principal == null) {
      return false;
    }
    if (principal.isSuperAdmin()) {
      return true;
    }
    return principal.isAdminOfTeam(teamId) && teamBelongsTo(orgId, teamId);
  }

  /** Organisation administrators may manage any team; team administrators only their own. */
  public boolean canManageTeam(UUID orgId, UUID teamId) {
    return isOrgAdmin(orgId) || isTeamAdmin(orgId, teamId);
  }

  public boolean isTeamMember(UUID orgId, UUID teamId) {
    AppPrincipal principal = principalOrNull();
    if (principal == null) {
      return false;
    }
    if (principal.isSuperAdmin()) {
      return true;
    }
    return principal.isMemberOfTeam(teamId) && teamBelongsTo(orgId, teamId);
  }

  public boolean isSelf(UUID userId) {
    AppPrincipal principal = principalOrNull();
    return principal != null && principal.userId().equals(userId);
  }

  private boolean teamBelongsTo(UUID orgId, UUID teamId) {
    return teams.findByIdAndOrgId(teamId, orgId).isPresent();
  }

  private AppPrincipal principalOrNull() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    return authentication instanceof AppAuthenticationToken token ? token.getPrincipal() : null;
  }
}
