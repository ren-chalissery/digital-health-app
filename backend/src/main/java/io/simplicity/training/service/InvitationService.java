package io.simplicity.training.service;

import io.simplicity.training.config.AppProperties;
import io.simplicity.training.exception.BadRequestException;
import io.simplicity.training.exception.ConflictException;
import io.simplicity.training.exception.NotFoundException;
import io.simplicity.training.model.Emails;
import io.simplicity.training.model.entity.AppUser;
import io.simplicity.training.model.entity.Invitation;
import io.simplicity.training.model.entity.OrgMembership;
import io.simplicity.training.model.entity.Organisation;
import io.simplicity.training.model.entity.Team;
import io.simplicity.training.model.entity.TeamMember;
import io.simplicity.training.model.enums.InvitationStatus;
import io.simplicity.training.model.enums.UserStatus;
import io.simplicity.training.model.request.CreateInvitationRequest;
import io.simplicity.training.model.response.InvitationPreviewResponse;
import io.simplicity.training.model.response.InvitationResponse;
import io.simplicity.training.repository.AppUserRepository;
import io.simplicity.training.repository.InvitationRepository;
import io.simplicity.training.repository.OrgMembershipRepository;
import io.simplicity.training.repository.OrganisationRepository;
import io.simplicity.training.repository.TeamMemberRepository;
import io.simplicity.training.repository.TeamRepository;
import io.simplicity.training.security.AppPrincipal;
import io.simplicity.training.security.SessionService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
@Slf4j
public class InvitationService {

  private static final String REDIS_KEY_PREFIX = "invite:";

  private final InvitationRepository invitations;
  private final OrganisationRepository organisations;
  private final OrgMembershipRepository orgMemberships;
  private final TeamRepository teams;
  private final TeamMemberRepository teamMembers;
  private final AppUserRepository users;
  private final AuditService audit;
  private final SessionService sessions;
  private final EmailSender email;
  private final RateLimiter rateLimiter;
  private final StringRedisTemplate redis;
  private final AppProperties properties;

  @Transactional
  public InvitationResponse create(
      AppPrincipal actor, UUID orgId, CreateInvitationRequest request) {

    // Refused if Redis is down. This limit is the only thing standing between a compromised
    // administrator account and a bulk mailer, so an unenforceable one is worse than an outage.
    if (!rateLimiter.tryAcquire(
        "invite",
        orgId.toString(),
        properties.invitations().maxPerHourPerOrg(),
        Duration.ofHours(1),
        RateLimiter.OnOutage.REFUSE)) {
      throw new ConflictException(
          "This organisation has sent too many invitations in the last hour. Please try again later.");
    }

    Organisation organisation =
        organisations.findById(orgId).orElseThrow(() -> NotFoundException.of("Organisation", orgId));
    String recipient = Emails.normalise(request.email());

    Team team = null;
    if (request.teamId() != null) {
      team =
          teams
              .findByIdAndOrgId(request.teamId(), orgId)
              .orElseThrow(() -> NotFoundException.of("Team", request.teamId()));
    } else if (request.teamRole() != null) {
      throw new BadRequestException("A team role only makes sense together with a team");
    }

    users
        .findByEmail(recipient)
        .flatMap(user -> orgMemberships.find(user.getId(), orgId))
        .ifPresent(
            membership -> {
              throw new ConflictException("That person is already a member of this organisation");
            });

    // Re-inviting is idempotent from the administrator's point of view: the outstanding invitation
    // is withdrawn and a fresh link issued, rather than leaving two valid tokens in circulation.
    Optional<Invitation> outstanding =
        invitations.findByOrgIdAndEmailAndStatus(orgId, recipient, InvitationStatus.PENDING);
    outstanding.ifPresent(
        previous -> {
          previous.setStatus(InvitationStatus.REVOKED);
          invitations.saveAndFlush(previous);
          forgetToken(previous.getTokenHash());
        });

    String rawToken = InvitationTokens.generate();
    String tokenHash = InvitationTokens.hash(rawToken);
    Instant expiresAt = Instant.now().plus(properties.invitations().ttl());

    Invitation invitation =
        invitations.save(
            Invitation.builder()
                .orgId(orgId)
                .teamId(team == null ? null : team.getId())
                .email(recipient)
                .orgRole(request.orgRole())
                .teamRole(team == null ? null : request.teamRole())
                .tokenHash(tokenHash)
                .invitedBy(actor.userId())
                .expiresAt(expiresAt)
                .build());

    rememberToken(tokenHash, invitation.getId(), properties.invitations().ttl());
    email.send(
        recipient,
        "You have been invited to " + organisation.getName(),
        invitationHtml(organisation, rawToken),
        invitationText(organisation, rawToken));

    audit.record(actor.userId(), orgId, "INVITATION_CREATED", "invitation", invitation.getId());
    return toResponse(invitation, team == null ? null : team.getName());
  }

  @Transactional(readOnly = true)
  public List<InvitationResponse> list(UUID orgId) {
    return invitations.findByOrgIdOrderByCreatedAtDesc(orgId).stream()
        .map(invitation -> toResponse(invitation, teamName(invitation)))
        .toList();
  }

  @Transactional
  public void revoke(AppPrincipal actor, UUID orgId, UUID invitationId) {
    Invitation invitation =
        invitations
            .findByIdAndOrgId(invitationId, orgId)
            .orElseThrow(() -> NotFoundException.of("Invitation", invitationId));

    if (invitation.getStatus() != InvitationStatus.PENDING) {
      throw new ConflictException("That invitation is no longer outstanding");
    }

    invitation.setStatus(InvitationStatus.REVOKED);
    invitations.save(invitation);
    forgetToken(invitation.getTokenHash());

    audit.record(actor.userId(), orgId, "INVITATION_REVOKED", "invitation", invitationId);
  }

  /**
   * Public, so the recipient can see who invited them before creating an account. An unknown,
   * expired, or already-used token produces the same "not valid" answer rather than an error, so
   * the endpoint cannot be used to test which tokens exist.
   */
  @Transactional(readOnly = true)
  public InvitationPreviewResponse preview(String rawToken) {
    Optional<Invitation> found = invitations.findByTokenHash(InvitationTokens.hash(rawToken));
    if (found.isEmpty() || !found.get().isRedeemable(Instant.now())) {
      return new InvitationPreviewResponse(null, null, null, null, false);
    }

    Invitation invitation = found.get();
    Organisation organisation = organisations.findById(invitation.getOrgId()).orElse(null);
    if (organisation == null) {
      return new InvitationPreviewResponse(null, null, null, null, false);
    }

    return new InvitationPreviewResponse(
        organisation.getName(),
        teamName(invitation),
        invitation.getOrgRole(),
        invitation.getEmail(),
        true);
  }

  /**
   * Redeems an invitation for the signed-in user. The organisation membership and any team
   * membership are created in one transaction, so a failure cannot leave somebody in a team
   * belonging to an organisation they are not in.
   */
  @Transactional
  public void accept(AppPrincipal principal, String rawToken) {
    Invitation invitation =
        invitations
            .findByTokenHash(InvitationTokens.hash(rawToken))
            .orElseThrow(() -> new NotFoundException("That invitation link is not valid"));

    if (invitation.getStatus() != InvitationStatus.PENDING) {
      throw new ConflictException("That invitation has already been used or withdrawn");
    }
    if (invitation.isExpired(Instant.now())) {
      invitation.setStatus(InvitationStatus.EXPIRED);
      invitations.save(invitation);
      throw new ConflictException("That invitation has expired. Ask for a new one.");
    }

    AppUser user =
        users
            .findById(principal.userId())
            .orElseThrow(() -> NotFoundException.of("User", principal.userId()));

    // The invitation names an address. Letting a different account redeem it would turn a leaked
    // link into access for whoever found it.
    if (!invitation.getEmail().equalsIgnoreCase(user.getEmail())) {
      throw new BadRequestException(
          "This invitation was sent to " + invitation.getEmail() + ". Sign in as that person to accept it.");
    }

    if (orgMemberships.find(user.getId(), invitation.getOrgId()).isEmpty()) {
      orgMemberships.save(
          OrgMembership.of(user.getId(), invitation.getOrgId(), invitation.getOrgRole()));
    }
    if (invitation.getTeamId() != null
        && teamMembers.find(invitation.getTeamId(), user.getId()).isEmpty()) {
      teamMembers.save(
          TeamMember.of(
              invitation.getTeamId(),
              user.getId(),
              invitation.getTeamRole() == null
                  ? io.simplicity.training.model.enums.TeamRole.TEAM_MEMBER
                  : invitation.getTeamRole()));
    }

    if (user.getStatus() == UserStatus.INVITED) {
      user.setStatus(UserStatus.ACTIVE);
      users.save(user);
    }

    invitation.setStatus(InvitationStatus.ACCEPTED);
    invitation.setAcceptedAt(Instant.now());
    invitations.save(invitation);
    forgetToken(invitation.getTokenHash());

    audit.record(
        user.getId(),
        invitation.getOrgId(),
        "INVITATION_ACCEPTED",
        "invitation",
        invitation.getId());
    sessions.rolesChanged(user.getId());
  }

  /**
   * Postgres remains authoritative; this index exists so an invitation disappears from the fast
   * path the moment it is withdrawn, and expires by itself. Only the digest is stored — putting
   * the raw token in Redis would undo the point of hashing it in the database.
   */
  private void rememberToken(String tokenHash, UUID invitationId, Duration ttl) {
    afterCommit(
        () -> {
          try {
            redis.opsForValue().set(REDIS_KEY_PREFIX + tokenHash, invitationId.toString(), ttl);
          } catch (DataAccessException e) {
            log.warn("Could not index invitation {} in Redis", invitationId, e);
          }
        });
  }

  private void forgetToken(String tokenHash) {
    afterCommit(
        () -> {
          try {
            redis.delete(REDIS_KEY_PREFIX + tokenHash);
          } catch (DataAccessException e) {
            log.warn("Could not drop the Redis index for a withdrawn invitation", e);
          }
        });
  }

  /**
   * Redis takes no part in the transaction, so an index written before a rollback would describe a
   * row that never existed, and a key deleted before one would drop the index of an invitation
   * still outstanding. Waiting for the commit keeps the two from disagreeing.
   */
  private void afterCommit(Runnable action) {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      action.run();
      return;
    }
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            action.run();
          }
        });
  }

  private String teamName(Invitation invitation) {
    return invitation.getTeamId() == null
        ? null
        : teams.findById(invitation.getTeamId()).map(Team::getName).orElse(null);
  }

  private InvitationResponse toResponse(Invitation invitation, String teamName) {
    return new InvitationResponse(
        invitation.getId(),
        invitation.getEmail(),
        invitation.getOrgRole(),
        invitation.getTeamId(),
        teamName,
        invitation.getTeamRole(),
        invitation.getStatus(),
        invitation.getExpiresAt(),
        invitation.getCreatedAt());
  }

  private String acceptUrl(String rawToken) {
    return properties.web().baseUrl() + "/invitations/" + rawToken;
  }

  private String invitationText(Organisation organisation, String rawToken) {
    return """
        You have been invited to join %s on the Simplicity training platform.

        Open this link to accept:
        %s

        The link expires in %d days. If you were not expecting this, you can ignore it.
        """
        .formatted(organisation.getName(), acceptUrl(rawToken), properties.invitations().ttl().toDays());
  }

  private String invitationHtml(Organisation organisation, String rawToken) {
    return """
        <p>You have been invited to join <strong>%s</strong> on the Simplicity training platform.</p>
        <p><a href="%s">Accept the invitation</a></p>
        <p>The link expires in %d days. If you were not expecting this, you can ignore it.</p>
        """
        .formatted(organisation.getName(), acceptUrl(rawToken), properties.invitations().ttl().toDays());
  }
}
