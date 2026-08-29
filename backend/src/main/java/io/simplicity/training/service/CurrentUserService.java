package io.simplicity.training.service;

import io.simplicity.training.model.entity.AppUser;
import io.simplicity.training.model.entity.Organisation;
import io.simplicity.training.model.entity.Team;
import io.simplicity.training.model.entity.TeamMember;
import io.simplicity.training.model.response.CurrentUserResponse;
import io.simplicity.training.model.response.OrganisationMembershipResponse;
import io.simplicity.training.model.response.TeamMembershipResponse;
import io.simplicity.training.repository.AppUserRepository;
import io.simplicity.training.repository.OrganisationRepository;
import io.simplicity.training.repository.TeamMemberRepository;
import io.simplicity.training.repository.TeamRepository;
import io.simplicity.training.security.AppPrincipal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Assembles the profile-and-memberships view that every client loads immediately after sign-in. */
@Service
@RequiredArgsConstructor
public class CurrentUserService {

  private final AppUserRepository users;
  private final OrganisationRepository organisations;
  private final TeamRepository teams;
  private final TeamMemberRepository teamMembers;

  @Transactional(readOnly = true)
  public CurrentUserResponse describe(AppPrincipal principal) {
    AppUser user =
        users
            .findById(principal.userId())
            .orElseThrow(
                () -> new IllegalStateException("Authenticated user no longer exists in the database"));

    List<OrganisationMembershipResponse> memberships = new ArrayList<>();
    for (Map.Entry<UUID, ?> entry : principal.orgRoles().entrySet()) {
      UUID orgId = entry.getKey();
      Organisation org = organisations.findById(orgId).orElse(null);
      if (org == null) {
        continue;
      }
      memberships.add(
          new OrganisationMembershipResponse(
              org.getId(),
              org.getName(),
              org.getSlug(),
              org.getOrganisationType(),
              principal.orgRoles().get(orgId),
              teamsWithin(principal, orgId)));
    }

    return new CurrentUserResponse(
        user.getId(),
        user.getEmail(),
        user.getFullName(),
        user.getPhone(),
        user.getProfessionalRole(),
        user.isProfileCompleted(),
        user.getStatus(),
        user.getPlatformRole(),
        List.copyOf(memberships));
  }

  private List<TeamMembershipResponse> teamsWithin(AppPrincipal principal, UUID orgId) {
    List<TeamMember> membershipsInOrg =
        teamMembers.findByUserIdAndOrgId(principal.userId(), orgId);
    if (membershipsInOrg.isEmpty()) {
      return List.of();
    }
    Map<UUID, Team> byId =
        teams.findAllById(membershipsInOrg.stream().map(TeamMember::getTeamId).toList()).stream()
            .collect(Collectors.toMap(Team::getId, Function.identity()));

    return membershipsInOrg.stream()
        .map(member -> byId.get(member.getTeamId()))
        .filter(team -> team != null)
        .map(
            team ->
                new TeamMembershipResponse(
                    team.getId(), team.getName(), principal.teamRoles().get(team.getId())))
        .toList();
  }
}
