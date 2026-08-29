package io.simplicity.training.service;

import io.simplicity.training.exception.BadRequestException;
import io.simplicity.training.exception.ConflictException;
import io.simplicity.training.exception.NotFoundException;
import io.simplicity.training.model.entity.AppUser;
import io.simplicity.training.model.entity.Team;
import io.simplicity.training.model.entity.TeamMember;
import io.simplicity.training.model.enums.TeamRole;
import io.simplicity.training.model.request.CreateTeamRequest;
import io.simplicity.training.model.request.UpdateTeamRequest;
import io.simplicity.training.model.response.TeamMemberDetailResponse;
import io.simplicity.training.model.response.TeamResponse;
import io.simplicity.training.repository.AppUserRepository;
import io.simplicity.training.repository.OrgMembershipRepository;
import io.simplicity.training.repository.TeamMemberRepository;
import io.simplicity.training.repository.TeamRepository;
import io.simplicity.training.security.AppPrincipal;
import io.simplicity.training.security.SessionService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Every read and write takes the organisation id as well as the team id. The repository queries
 * filter on {@code org_id}, so a team belonging to another tenant is simply not found rather than
 * relying on the authorisation layer alone.
 */
@Service
@RequiredArgsConstructor
public class TeamService {

  private final TeamRepository teams;
  private final TeamMemberRepository teamMembers;
  private final OrgMembershipRepository orgMemberships;
  private final AppUserRepository users;
  private final AuditService audit;
  private final SessionService sessions;

  @Transactional(readOnly = true)
  public List<TeamResponse> list(UUID orgId) {
    return teams.findByOrgIdOrderByNameAsc(orgId).stream().map(this::toResponse).toList();
  }

  @Transactional(readOnly = true)
  public TeamResponse get(UUID orgId, UUID teamId) {
    return toResponse(requireTeam(orgId, teamId));
  }

  @Transactional
  public TeamResponse create(AppPrincipal actor, UUID orgId, CreateTeamRequest request) {
    String name = request.name().trim();
    if (teams.existsByOrgIdAndNameIgnoreCase(orgId, name)) {
      throw new ConflictException("A team called '" + name + "' already exists");
    }

    Team team =
        teams.save(
            Team.builder()
                .orgId(orgId)
                .name(name)
                .description(request.description())
                .createdBy(actor.userId())
                .build());

    audit.record(actor.userId(), orgId, "TEAM_CREATED", "team", team.getId());
    return toResponse(team);
  }

  @Transactional
  public TeamResponse update(AppPrincipal actor, UUID orgId, UUID teamId, UpdateTeamRequest request) {
    Team team = requireTeam(orgId, teamId);
    String name = request.name().trim();
    if (!team.getName().equalsIgnoreCase(name) && teams.existsByOrgIdAndNameIgnoreCase(orgId, name)) {
      throw new ConflictException("A team called '" + name + "' already exists");
    }

    team.setName(name);
    team.setDescription(request.description());
    teams.save(team);

    audit.record(actor.userId(), orgId, "TEAM_UPDATED", "team", teamId);
    return toResponse(team);
  }

  @Transactional
  public void delete(AppPrincipal actor, UUID orgId, UUID teamId) {
    Team team = requireTeam(orgId, teamId);
    List<TeamMember> members = teamMembers.findByTeamId(teamId);

    teamMembers.deleteAll(members);
    teams.delete(team);

    audit.record(actor.userId(), orgId, "TEAM_DELETED", "team", teamId);
    members.forEach(member -> sessions.rolesChanged(member.getUserId()));
  }

  @Transactional(readOnly = true)
  public List<TeamMemberDetailResponse> listMembers(UUID orgId, UUID teamId) {
    requireTeam(orgId, teamId);
    List<TeamMember> members = teamMembers.findByTeamId(teamId);
    Map<UUID, AppUser> byId =
        users.findAllById(members.stream().map(TeamMember::getUserId).toList()).stream()
            .collect(Collectors.toMap(AppUser::getId, Function.identity()));

    return members.stream()
        .filter(member -> byId.containsKey(member.getUserId()))
        .map(
            member -> {
              AppUser user = byId.get(member.getUserId());
              return new TeamMemberDetailResponse(
                  user.getId(),
                  user.getEmail(),
                  user.getFullName(),
                  user.getProfessionalRole(),
                  member.getTeamRole(),
                  member.getJoinedAt());
            })
        .toList();
  }

  @Transactional
  public TeamMemberDetailResponse addMember(
      AppPrincipal actor, UUID orgId, UUID teamId, UUID userId, TeamRole role) {
    requireTeam(orgId, teamId);

    // Team membership cannot be a way into an organisation. The user has to already belong to it.
    if (orgMemberships.find(userId, orgId).isEmpty()) {
      throw new BadRequestException(
          "That user is not a member of this organisation, so they cannot join one of its teams");
    }
    if (teamMembers.find(teamId, userId).isPresent()) {
      throw new ConflictException("That user is already in this team");
    }

    teamMembers.save(TeamMember.of(teamId, userId, role));
    audit.record(
        actor.userId(), orgId, "TEAM_MEMBER_ADDED", "team", teamId, "{\"userId\":\"" + userId + "\"}");
    sessions.rolesChanged(userId);

    return listMembers(orgId, teamId).stream()
        .filter(member -> member.userId().equals(userId))
        .findFirst()
        .orElseThrow();
  }

  @Transactional
  public void removeMember(AppPrincipal actor, UUID orgId, UUID teamId, UUID userId) {
    requireTeam(orgId, teamId);
    TeamMember member =
        teamMembers
            .find(teamId, userId)
            .orElseThrow(() -> NotFoundException.of("Team membership for user", userId));

    teamMembers.delete(member);
    audit.record(
        actor.userId(),
        orgId,
        "TEAM_MEMBER_REMOVED",
        "team",
        teamId,
        "{\"userId\":\"" + userId + "\"}");
    sessions.rolesChanged(userId);
  }

  private Team requireTeam(UUID orgId, UUID teamId) {
    return teams
        .findByIdAndOrgId(teamId, orgId)
        .orElseThrow(() -> NotFoundException.of("Team", teamId));
  }

  private TeamResponse toResponse(Team team) {
    return new TeamResponse(
        team.getId(),
        team.getOrgId(),
        team.getName(),
        team.getDescription(),
        teamMembers.findByTeamId(team.getId()).size(),
        team.getCreatedAt());
  }
}
