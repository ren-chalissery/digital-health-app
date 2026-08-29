package io.simplicity.training.security;

import io.simplicity.training.model.entity.AppUser;
import io.simplicity.training.model.entity.OrgMembership;
import io.simplicity.training.model.entity.TeamMember;
import io.simplicity.training.model.enums.MembershipStatus;
import io.simplicity.training.model.enums.OrgRole;
import io.simplicity.training.model.enums.TeamRole;
import io.simplicity.training.repository.OrgMembershipRepository;
import io.simplicity.training.repository.OrganisationRepository;
import io.simplicity.training.repository.TeamMemberRepository;
import io.simplicity.training.service.UserProvisioningService;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Turns a Cognito subject into an {@link AppPrincipal}, provisioning the user if this is their
 * first request.
 *
 * <p>This class deliberately knows nothing about caching. {@link CachingPrincipalService} wraps it,
 * which keeps the "how do we work out who this is" logic testable without Redis.
 */
@Service
@RequiredArgsConstructor
public class PrincipalService implements PrincipalLookup {

  private final UserProvisioningService provisioning;
  private final OrgMembershipRepository orgMemberships;
  private final OrganisationRepository organisations;
  private final TeamMemberRepository teamMembers;

  @Override
  @Transactional
  public AppPrincipal resolve(String cognitoSub, Supplier<Optional<String>> verifiedEmail) {
    AppUser user = provisioning.findOrCreate(cognitoSub, verifiedEmail);
    return forUser(user);
  }

  @Transactional(readOnly = true)
  public AppPrincipal forUser(AppUser user) {
    Set<UUID> archived = organisations.findArchivedIds();

    Map<UUID, OrgRole> orgRoles = new LinkedHashMap<>();
    for (OrgMembership membership : orgMemberships.findByUserId(user.getId())) {
      // A suspended membership confers nothing, and neither does membership of an archived
      // organisation. Both are left out of the principal entirely rather than carried along for
      // every authorisation check to remember to exclude: filtering here is what makes the
      // existing @PreAuthorize expressions refuse an archived organisation without being touched,
      // and what stops its data being reachable by anyone who still knows the id.
      if (membership.getStatus() == MembershipStatus.ACTIVE
          && !archived.contains(membership.getOrgId())) {
        orgRoles.put(membership.getOrgId(), membership.getOrgRole());
      }
    }

    Map<UUID, TeamRole> teamRoles = new LinkedHashMap<>();
    for (TeamMember member : teamMembers.findByUserId(user.getId())) {
      teamRoles.put(member.getTeamId(), member.getTeamRole());
    }

    return new AppPrincipal(
        user.getId(),
        user.getCognitoSub(),
        user.getEmail(),
        user.isProfileCompleted(),
        user.getStatus(),
        user.getPlatformRole(),
        Map.copyOf(orgRoles),
        Map.copyOf(teamRoles));
  }
}
