package io.simplicity.training.service;

import io.simplicity.training.exception.BadRequestException;
import io.simplicity.training.exception.ConflictException;
import io.simplicity.training.exception.NotFoundException;
import io.simplicity.training.model.entity.AppUser;
import io.simplicity.training.model.entity.OrgMembership;
import io.simplicity.training.model.entity.Organisation;
import io.simplicity.training.model.enums.MembershipStatus;
import io.simplicity.training.model.enums.OrgRole;
import io.simplicity.training.model.request.CreateOrganisationRequest;
import io.simplicity.training.model.response.OrgMemberResponse;
import io.simplicity.training.model.response.OrganisationResponse;
import io.simplicity.training.repository.AppUserRepository;
import io.simplicity.training.repository.OrgMembershipRepository;
import io.simplicity.training.repository.OrganisationRepository;
import io.simplicity.training.repository.TeamMemberRepository;
import io.simplicity.training.security.AppPrincipal;
import io.simplicity.training.security.SessionService;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrganisationService {

  private static final int MAX_SLUG_ATTEMPTS = 50;

  private final OrganisationRepository organisations;
  private final OrgMembershipRepository orgMemberships;
  private final TeamMemberRepository teamMembers;
  private final AppUserRepository users;
  private final AuditService audit;
  private final SessionService sessions;

  @Transactional
  public OrganisationResponse create(AppPrincipal principal, CreateOrganisationRequest request) {
    Organisation organisation =
        organisations.save(
            Organisation.builder()
                .name(request.name().trim())
                .slug(uniqueSlug(request.name()))
                .organisationType(request.organisationType())
                .country(request.country())
                .build());

    orgMemberships.save(
        OrgMembership.of(principal.userId(), organisation.getId(), OrgRole.ORG_ADMIN));

    audit.record(
        principal.userId(),
        organisation.getId(),
        "ORGANISATION_CREATED",
        "organisation",
        organisation.getId());

    sessions.rolesChanged(principal.userId());
    return toResponse(organisation);
  }

  @Transactional(readOnly = true)
  public OrganisationResponse get(UUID orgId) {
    return toResponse(
        organisations.findById(orgId).orElseThrow(() -> NotFoundException.of("Organisation", orgId)));
  }

  @Transactional(readOnly = true)
  public List<OrgMemberResponse> listMembers(UUID orgId) {
    List<OrgMembership> memberships = orgMemberships.findByOrgId(orgId);
    Map<UUID, AppUser> byId =
        users.findAllById(memberships.stream().map(OrgMembership::getUserId).toList()).stream()
            .collect(Collectors.toMap(AppUser::getId, Function.identity()));

    return memberships.stream()
        .map(membership -> byId.get(membership.getUserId()))
        .filter(user -> user != null)
        .map(
            user -> {
              OrgMembership membership =
                  memberships.stream()
                      .filter(m -> m.getUserId().equals(user.getId()))
                      .findFirst()
                      .orElseThrow();
              return new OrgMemberResponse(
                  user.getId(),
                  user.getEmail(),
                  user.getFullName(),
                  user.getProfessionalRole(),
                  membership.getOrgRole(),
                  membership.getStatus(),
                  user.getStatus(),
                  membership.getJoinedAt());
            })
        .toList();
  }

  @Transactional
  public OrgMemberResponse changeRole(
      AppPrincipal actor, UUID orgId, UUID userId, OrgRole newRole) {
    OrgMembership membership =
        orgMemberships
            .find(userId, orgId)
            .orElseThrow(() -> NotFoundException.of("Membership for user", userId));

    if (membership.getOrgRole() == OrgRole.ORG_ADMIN
        && newRole != OrgRole.ORG_ADMIN
        && orgMemberships.countActiveAdmins(orgId) <= 1) {
      // Without this an organisation can be left with nobody able to administer it, which is only
      // recoverable by hand.
      throw new ConflictException(
          "This is the organisation's last administrator, so their role cannot be changed");
    }

    membership.setOrgRole(newRole);
    orgMemberships.save(membership);
    audit.record(actor.userId(), orgId, "ORG_ROLE_CHANGED", "user", userId, "{\"role\":\"" + newRole + "\"}");
    sessions.rolesChanged(userId);

    return listMembers(orgId).stream()
        .filter(member -> member.userId().equals(userId))
        .findFirst()
        .orElseThrow();
  }

  @Transactional
  public void removeMember(AppPrincipal actor, UUID orgId, UUID userId) {
    OrgMembership membership =
        orgMemberships
            .find(userId, orgId)
            .orElseThrow(() -> NotFoundException.of("Membership for user", userId));

    if (membership.getOrgRole() == OrgRole.ORG_ADMIN
        && orgMemberships.countActiveAdmins(orgId) <= 1) {
      throw new ConflictException(
          "This is the organisation's last administrator, so they cannot be removed");
    }
    if (actor.userId().equals(userId)) {
      throw new BadRequestException("Use the leave endpoint to remove your own membership");
    }

    // Leaving the organisation must also end every team membership inside it, or the user keeps
    // team roles for teams they can no longer legitimately reach.
    teamMembers.deleteAllForUserInOrg(userId, orgId);
    orgMemberships.delete(membership);

    audit.record(actor.userId(), orgId, "ORG_MEMBER_REMOVED", "user", userId);
    sessions.rolesChanged(userId);
  }

  private OrganisationResponse toResponse(Organisation organisation) {
    return new OrganisationResponse(
        organisation.getId(),
        organisation.getName(),
        organisation.getSlug(),
        organisation.getOrganisationType(),
        organisation.getCountry(),
        organisation.getCreatedAt());
  }

  /**
   * Slugs are derived from the name and must be unique across the platform, so a numeric suffix is
   * appended until one is free.
   */
  private String uniqueSlug(String name) {
    String base = slugify(name);
    if (base.isEmpty()) {
      throw new BadRequestException("Organisation name must contain at least one letter or digit");
    }
    if (!organisations.existsBySlug(base)) {
      return base;
    }
    for (int suffix = 2; suffix < MAX_SLUG_ATTEMPTS; suffix++) {
      String candidate = base + "-" + suffix;
      if (!organisations.existsBySlug(candidate)) {
        return candidate;
      }
    }
    return base + "-" + UUID.randomUUID().toString().substring(0, 8);
  }

  private String slugify(String name) {
    String withoutAccents =
        Normalizer.normalize(name, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    return withoutAccents
        .toLowerCase(Locale.ROOT)
        .replaceAll("[^a-z0-9]+", "-")
        .replaceAll("(^-|-$)", "");
  }
}
