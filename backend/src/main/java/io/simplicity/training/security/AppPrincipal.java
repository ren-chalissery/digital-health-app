package io.simplicity.training.security;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.simplicity.training.model.enums.OrgRole;
import io.simplicity.training.model.enums.PlatformRole;
import io.simplicity.training.model.enums.TeamRole;
import io.simplicity.training.model.enums.UserStatus;
import java.io.Serializable;
import java.util.Map;
import java.util.UUID;

/**
 * Everything the authorisation layer needs about the caller, resolved once per request from the
 * Cognito subject and then cached in Redis.
 *
 * <p>Serializable because it is cached; keep it to plain values so the cached form stays stable.
 */
public record AppPrincipal(
    UUID userId,
    String cognitoSub,
    String email,
    boolean profileCompleted,
    UserStatus status,
    PlatformRole platformRole,
    Map<UUID, OrgRole> orgRoles,
    Map<UUID, TeamRole> teamRoles)
    implements Serializable {

  // Derived, so excluded from the cached representation: Jackson would otherwise write them as
  // extra properties that the canonical constructor has no parameters for, and every read back
  // would fail.
  @JsonIgnore
  public boolean isSuperAdmin() {
    return platformRole == PlatformRole.SUPER_ADMIN;
  }

  @JsonIgnore
  public boolean isActive() {
    return status == UserStatus.ACTIVE;
  }

  public boolean isMemberOf(UUID orgId) {
    return orgRoles.containsKey(orgId);
  }

  public boolean isAdminOf(UUID orgId) {
    return orgRoles.get(orgId) == OrgRole.ORG_ADMIN;
  }

  public boolean isAdminOfTeam(UUID teamId) {
    return teamRoles.get(teamId) == TeamRole.TEAM_ADMIN;
  }

  public boolean isMemberOfTeam(UUID teamId) {
    return teamRoles.containsKey(teamId);
  }
}
