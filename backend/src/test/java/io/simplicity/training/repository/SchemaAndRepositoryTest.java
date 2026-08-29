package io.simplicity.training.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.simplicity.training.model.entity.AppUser;
import io.simplicity.training.model.entity.AuditEvent;
import io.simplicity.training.model.entity.Invitation;
import io.simplicity.training.model.entity.OrgMembership;
import io.simplicity.training.model.entity.Organisation;
import io.simplicity.training.model.entity.Team;
import io.simplicity.training.model.entity.TeamMember;
import io.simplicity.training.model.enums.InvitationStatus;
import io.simplicity.training.model.enums.OrgRole;
import io.simplicity.training.model.enums.OrganisationType;
import io.simplicity.training.model.enums.TeamRole;
import io.simplicity.training.support.AbstractDataJpaTest;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class SchemaAndRepositoryTest extends AbstractDataJpaTest {

  @Autowired private EntityManager entityManager;
  @Autowired private AppUserRepository users;
  @Autowired private OrganisationRepository organisations;
  @Autowired private OrgMembershipRepository memberships;
  @Autowired private TeamRepository teams;
  @Autowired private TeamMemberRepository teamMembers;
  @Autowired private InvitationRepository invitations;
  @Autowired private AuditEventRepository auditEvents;

  // Flush on save: Hibernate defers the INSERT, and the @CreationTimestamp values are only
  // generated when it runs.
  private Organisation newOrg(String slug) {
    return organisations.saveAndFlush(
        Organisation.builder()
            .name(slug)
            .slug(slug)
            .organisationType(OrganisationType.HOSPITAL)
            .country("NZ")
            .build());
  }

  private AppUser newUser(String email) {
    return users.saveAndFlush(AppUser.builder().email(email).cognitoSub("sub-" + email).build());
  }

  @Test
  void persistsAnOrganisationWithGeneratedIdAndTimestamps() {
    Organisation saved = newOrg("north-shore");

    assertThat(saved.getId()).isNotNull();
    assertThat(saved.getCreatedAt()).isNotNull();
    assertThat(saved.getUpdatedAt()).isNotNull();
    assertThat(organisations.findBySlug("north-shore")).isPresent();
  }

  @Test
  void storesEmailLowercasedWhateverCaseTheCallerUsed() {
    AppUser saved = newUser("Clinician@Example.Org");

    assertThat(saved.getEmail()).isEqualTo("clinician@example.org");
  }

  @Test
  void findsAUserWhateverCaseTheLookupUses() {
    newUser("Clinician@Example.Org");

    assertThat(users.findByEmail("CLINICIAN@EXAMPLE.ORG")).isPresent();
    assertThat(users.findByEmail("  clinician@example.org  ")).isPresent();
    assertThat(users.existsByEmail("Clinician@Example.Org")).isTrue();
  }

  @Test
  void refusesTwoAccountsWhoseAddressesDifferOnlyInCase() {
    newUser("Duplicate@Example.Org");

    // Normalisation alone would catch this, but the citext unique index is what guarantees it
    // even if some future code path writes the column directly.
    users.save(AppUser.builder().email("duplicate@example.org").cognitoSub("sub-other").build());

    assertThatThrownBy(() -> entityManager.flush()).isInstanceOf(Exception.class);
  }

  @Test
  void newUsersStartIncompleteSoTheClientShowsTheProfileWizard() {
    AppUser saved = newUser("fresh@example.org");

    assertThat(saved.isProfileCompleted()).isFalse();
  }

  @Test
  void supportsOneUserBelongingToSeveralOrganisations() {
    AppUser locum = newUser("locum@example.org");
    Organisation a = newOrg("hospital-a");
    Organisation b = newOrg("hospital-b");

    memberships.save(OrgMembership.of(locum.getId(), a.getId(), OrgRole.ORG_ADMIN));
    memberships.save(OrgMembership.of(locum.getId(), b.getId(), OrgRole.ORG_MEMBER));

    List<OrgMembership> found = memberships.findByUserId(locum.getId());

    assertThat(found).hasSize(2);
    assertThat(found).extracting(OrgMembership::getOrgId).containsExactlyInAnyOrder(a.getId(), b.getId());
  }

  @Test
  void countsActiveAdministratorsSoTheLastOneCannotBeRemoved() {
    Organisation org = newOrg("counting-org");
    memberships.save(OrgMembership.of(newUser("admin1@example.org").getId(), org.getId(), OrgRole.ORG_ADMIN));
    memberships.save(OrgMembership.of(newUser("admin2@example.org").getId(), org.getId(), OrgRole.ORG_ADMIN));
    memberships.save(OrgMembership.of(newUser("member@example.org").getId(), org.getId(), OrgRole.ORG_MEMBER));

    assertThat(memberships.countActiveAdmins(org.getId())).isEqualTo(2);
  }

  @Test
  void findsATeamOnlyWithinItsOwnOrganisation() {
    Organisation owner = newOrg("owning-org");
    Organisation other = newOrg("other-org");
    Team team = teams.save(Team.builder().orgId(owner.getId()).name("Ward 3").build());

    assertThat(teams.findByIdAndOrgId(team.getId(), owner.getId())).isPresent();
    assertThat(teams.findByIdAndOrgId(team.getId(), other.getId()))
        .as("a team must never be reachable through another organisation's id")
        .isEmpty();
  }

  @Test
  void rejectsTwoTeamsWithTheSameNameInOneOrganisation() {
    Organisation org = newOrg("dup-team-org");
    teams.save(Team.builder().orgId(org.getId()).name("Crisis Team").build());
    teams.save(Team.builder().orgId(org.getId()).name("Crisis Team").build());

    assertThatThrownBy(() -> entityManager.flush()).isInstanceOf(Exception.class);
  }

  @Test
  void scopesTeamMembershipLookupToOneOrganisation() {
    AppUser user = newUser("multi@example.org");
    Organisation a = newOrg("scope-a");
    Organisation b = newOrg("scope-b");
    Team teamA = teams.save(Team.builder().orgId(a.getId()).name("Team A").build());
    Team teamB = teams.save(Team.builder().orgId(b.getId()).name("Team B").build());
    teamMembers.save(TeamMember.of(teamA.getId(), user.getId(), TeamRole.TEAM_ADMIN));
    teamMembers.save(TeamMember.of(teamB.getId(), user.getId(), TeamRole.TEAM_MEMBER));

    List<TeamMember> inA = teamMembers.findByUserIdAndOrgId(user.getId(), a.getId());

    assertThat(inA).hasSize(1);
    assertThat(inA.get(0).getTeamId()).isEqualTo(teamA.getId());
  }

  @Test
  void allowsOnlyOnePendingInvitationPerAddressPerOrganisation() {
    Organisation org = newOrg("invite-org");
    invitations.save(pendingInvitation(org.getId(), "invitee@example.org", "hash-1"));
    invitations.save(pendingInvitation(org.getId(), "invitee@example.org", "hash-2"));

    assertThatThrownBy(() -> entityManager.flush())
        .as("the partial unique index makes re-invitation idempotent in the database")
        .isInstanceOf(Exception.class);
  }

  @Test
  void allowsAFreshInvitationOnceTheEarlierOneIsRevoked() {
    Organisation org = newOrg("reinvite-org");
    Invitation first = invitations.save(pendingInvitation(org.getId(), "again@example.org", "hash-a"));
    first.setStatus(InvitationStatus.REVOKED);
    invitations.saveAndFlush(first);

    invitations.saveAndFlush(pendingInvitation(org.getId(), "again@example.org", "hash-b"));

    assertThat(invitations.findByOrgIdOrderByCreatedAtDesc(org.getId())).hasSize(2);
  }

  @Test
  void treatsAnInvitationPastItsExpiryAsNotRedeemable() {
    Invitation expired =
        pendingInvitation(UUID.randomUUID(), "old@example.org", "hash-old");
    expired.setExpiresAt(Instant.now().minus(1, ChronoUnit.DAYS));

    assertThat(expired.isRedeemable(Instant.now())).isFalse();
  }

  @Test
  void storesAuditMetadataAsJsonb() {
    Organisation org = newOrg("audit-org");
    AppUser actor = newUser("actor@example.org");

    auditEvents.saveAndFlush(
        AuditEvent.builder()
            .actorUserId(actor.getId())
            .orgId(org.getId())
            .action("TEAM_CREATED")
            .targetType("team")
            .targetId(UUID.randomUUID().toString())
            .metadata("{\"name\":\"Ward 3\"}")
            .build());

    assertThat(auditEvents.findByOrgIdOrderByCreatedAtDesc(org.getId())).hasSize(1);
  }

  private Invitation pendingInvitation(UUID orgId, String email, String tokenHash) {
    return Invitation.builder()
        .orgId(orgId)
        .email(email)
        .orgRole(OrgRole.ORG_MEMBER)
        .tokenHash(tokenHash)
        .expiresAt(Instant.now().plus(7, ChronoUnit.DAYS))
        .build();
  }
}
