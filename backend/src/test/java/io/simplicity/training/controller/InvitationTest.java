package io.simplicity.training.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.simplicity.training.exception.EmailDeliveryException;
import io.simplicity.training.model.entity.AppUser;
import io.simplicity.training.model.entity.Invitation;
import io.simplicity.training.model.entity.OrgMembership;
import io.simplicity.training.model.entity.Organisation;
import io.simplicity.training.model.entity.Team;
import io.simplicity.training.model.enums.InvitationStatus;
import io.simplicity.training.model.enums.OrgRole;
import io.simplicity.training.model.enums.OrganisationType;
import io.simplicity.training.model.enums.TeamRole;
import io.simplicity.training.service.InvitationTokens;
import io.simplicity.training.support.AbstractIntegrationTest;
import io.simplicity.training.support.RecordingEmailSender;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

@Import(RecordingEmailSender.Config.class)
class InvitationTest extends AbstractIntegrationTest {

  private static final String ADMIN_SUB = "sub-invite-admin";
  private static final String MEMBER_SUB = "sub-invite-member";
  private static final String INVITEE_SUB = "sub-invitee";
  private static final String INVITEE_EMAIL = "newcomer@example.org";

  @Autowired private RecordingEmailSender mail;

  private Organisation org;
  private AppUser admin;
  private AppUser member;
  private Team team;

  @BeforeEach
  void seed() {
    mail.clear();
    org =
        organisations.saveAndFlush(
            Organisation.builder()
                .name("Invite Hospital")
                .slug("invite-hospital")
                .organisationType(OrganisationType.HOSPITAL)
                .build());
    admin = user("invite-admin@example.org", ADMIN_SUB);
    member = user("invite-member@example.org", MEMBER_SUB);
    orgMemberships.saveAndFlush(OrgMembership.of(admin.getId(), org.getId(), OrgRole.ORG_ADMIN));
    orgMemberships.saveAndFlush(OrgMembership.of(member.getId(), org.getId(), OrgRole.ORG_MEMBER));
    team =
        teams.saveAndFlush(
            Team.builder().orgId(org.getId()).name("Ward 5").createdBy(admin.getId()).build());
  }

  @Test
  void invitesEmailsPreviewsAndAccepts() throws Exception {
    invite(INVITEE_EMAIL, OrgRole.ORG_MEMBER, team.getId(), TeamRole.TEAM_MEMBER);

    assertThat(mail.last().to()).isEqualTo(INVITEE_EMAIL);
    assertThat(mail.last().subject()).contains("Invite Hospital");

    String token = mail.lastToken();

    mockMvc
        .perform(get("/api/v1/invitations/" + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.valid").value(true))
        .andExpect(jsonPath("$.organisationName").value("Invite Hospital"))
        .andExpect(jsonPath("$.teamName").value("Ward 5"))
        .andExpect(jsonPath("$.orgRole").value("ORG_MEMBER"));

    // The newcomer signs in for the first time, which provisions them, then redeems the link.
    mockMvc
        .perform(
            get("/api/v1/me")
                .header(HttpHeaders.AUTHORIZATION, tokens.bearerFor(INVITEE_SUB, INVITEE_EMAIL)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.organisations.length()").value(0));

    mockMvc
        .perform(
            post("/api/v1/invitations/" + token + "/accept")
                .header(HttpHeaders.AUTHORIZATION, tokens.bearerFor(INVITEE_SUB, INVITEE_EMAIL)))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(
            get("/api/v1/me")
                .header(HttpHeaders.AUTHORIZATION, tokens.bearerFor(INVITEE_SUB, INVITEE_EMAIL)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.organisations.length()").value(1))
        .andExpect(jsonPath("$.organisations[0].orgRole").value("ORG_MEMBER"))
        .andExpect(jsonPath("$.organisations[0].teams.length()").value(1))
        .andExpect(jsonPath("$.organisations[0].teams[0].name").value("Ward 5"));
  }

  @Test
  void storesOnlyTheDigestOfTheToken() throws Exception {
    invite(INVITEE_EMAIL, OrgRole.ORG_MEMBER, null, null);
    String token = mail.lastToken();

    Invitation stored = invitations.findAll().get(0);
    assertThat(stored.getTokenHash()).isNotEqualTo(token);
    assertThat(stored.getTokenHash()).isEqualTo(InvitationTokens.hash(token));
  }

  @Test
  void createsBothMembershipsOrNeither() throws Exception {
    invite(INVITEE_EMAIL, OrgRole.ORG_ADMIN, team.getId(), TeamRole.TEAM_ADMIN);
    AppUser invitee = signIn(INVITEE_SUB, INVITEE_EMAIL);

    accept(mail.lastToken(), INVITEE_SUB, INVITEE_EMAIL);

    assertThat(orgMemberships.find(invitee.getId(), org.getId()))
        .get()
        .extracting(OrgMembership::getOrgRole)
        .isEqualTo(OrgRole.ORG_ADMIN);
    assertThat(teamMembers.find(team.getId(), invitee.getId()))
        .get()
        .extracting(tm -> tm.getTeamRole())
        .isEqualTo(TeamRole.TEAM_ADMIN);
  }

  @Test
  void reissuingWithdrawsThePreviousLink() throws Exception {
    invite(INVITEE_EMAIL, OrgRole.ORG_MEMBER, null, null);
    String firstToken = mail.lastToken();

    invite(INVITEE_EMAIL, OrgRole.ORG_ADMIN, null, null);
    String secondToken = mail.lastToken();

    assertThat(secondToken).isNotEqualTo(firstToken);
    mockMvc
        .perform(get("/api/v1/invitations/" + firstToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.valid").value(false));
    mockMvc
        .perform(get("/api/v1/invitations/" + secondToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.valid").value(true))
        .andExpect(jsonPath("$.orgRole").value("ORG_ADMIN"));

    assertThat(invitations.findAll())
        .as("only one invitation may be outstanding per address")
        .filteredOn(invitation -> invitation.getStatus() == InvitationStatus.PENDING)
        .hasSize(1);
  }

  @Test
  void refusesToRedeemAWithdrawnInvitation() throws Exception {
    invite(INVITEE_EMAIL, OrgRole.ORG_MEMBER, null, null);
    String token = mail.lastToken();
    UUID invitationId = invitations.findAll().get(0).getId();
    signIn(INVITEE_SUB, INVITEE_EMAIL);

    mockMvc
        .perform(
            delete("/api/v1/orgs/" + org.getId() + "/invitations/" + invitationId)
                .header(HttpHeaders.AUTHORIZATION, tokens.bearerFor(ADMIN_SUB)))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(
            post("/api/v1/invitations/" + token + "/accept")
                .header(HttpHeaders.AUTHORIZATION, tokens.bearerFor(INVITEE_SUB, INVITEE_EMAIL)))
        .andExpect(status().isConflict());
  }

  @Test
  void refusesToRedeemAnExpiredInvitation() throws Exception {
    invite(INVITEE_EMAIL, OrgRole.ORG_MEMBER, null, null);
    String token = mail.lastToken();
    signIn(INVITEE_SUB, INVITEE_EMAIL);

    Invitation invitation = invitations.findAll().get(0);
    invitation.setExpiresAt(Instant.now().minus(1, ChronoUnit.MINUTES));
    invitations.saveAndFlush(invitation);

    mockMvc
        .perform(
            post("/api/v1/invitations/" + token + "/accept")
                .header(HttpHeaders.AUTHORIZATION, tokens.bearerFor(INVITEE_SUB, INVITEE_EMAIL)))
        .andExpect(status().isConflict());

    mockMvc
        .perform(get("/api/v1/invitations/" + token))
        .andExpect(jsonPath("$.valid").value(false));
  }

  @Test
  void refusesToRedeemAnInvitationSentToSomebodyElse() throws Exception {
    invite(INVITEE_EMAIL, OrgRole.ORG_MEMBER, null, null);
    String token = mail.lastToken();
    signIn("sub-opportunist", "opportunist@example.org");

    mockMvc
        .perform(
            post("/api/v1/invitations/" + token + "/accept")
                .header(
                    HttpHeaders.AUTHORIZATION,
                    tokens.bearerFor("sub-opportunist", "opportunist@example.org")))
        .andExpect(status().isBadRequest());

    assertThat(orgMemberships.findByOrgId(org.getId())).hasSize(2);
  }

  @Test
  void refusesToRedeemTheSameInvitationTwice() throws Exception {
    invite(INVITEE_EMAIL, OrgRole.ORG_MEMBER, null, null);
    String token = mail.lastToken();
    signIn(INVITEE_SUB, INVITEE_EMAIL);

    accept(token, INVITEE_SUB, INVITEE_EMAIL);

    mockMvc
        .perform(
            post("/api/v1/invitations/" + token + "/accept")
                .header(HttpHeaders.AUTHORIZATION, tokens.bearerFor(INVITEE_SUB, INVITEE_EMAIL)))
        .andExpect(status().isConflict());
  }

  @Test
  void treatsAnUnknownTokenAsSimplyNotValid() throws Exception {
    mockMvc
        .perform(get("/api/v1/invitations/" + InvitationTokens.generate()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.valid").value(false))
        .andExpect(jsonPath("$.organisationName").doesNotExist());
  }

  @Test
  void refusesToInviteSomebodyWhoIsAlreadyAMember() throws Exception {
    mockMvc
        .perform(
            post(invitationsUrl())
                .header(HttpHeaders.AUTHORIZATION, tokens.bearerFor(ADMIN_SUB))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(member.getEmail(), OrgRole.ORG_MEMBER, null, null)))
        .andExpect(status().isConflict());
  }

  @Test
  void refusesToInviteIntoATeamFromAnotherOrganisation() throws Exception {
    Organisation other =
        organisations.saveAndFlush(
            Organisation.builder()
                .name("Other Clinic")
                .slug("other-clinic")
                .organisationType(OrganisationType.CLINIC)
                .build());
    Team foreignTeam =
        teams.saveAndFlush(
            Team.builder().orgId(other.getId()).name("Theirs").createdBy(admin.getId()).build());

    mockMvc
        .perform(
            post(invitationsUrl())
                .header(HttpHeaders.AUTHORIZATION, tokens.bearerFor(ADMIN_SUB))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(INVITEE_EMAIL, OrgRole.ORG_MEMBER, foreignTeam.getId(), null)))
        .andExpect(status().isNotFound());
  }

  @Test
  void onlyAdministratorsMayInviteListOrWithdraw() throws Exception {
    mockMvc
        .perform(
            post(invitationsUrl())
                .header(HttpHeaders.AUTHORIZATION, tokens.bearerFor(MEMBER_SUB))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(INVITEE_EMAIL, OrgRole.ORG_MEMBER, null, null)))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(get(invitationsUrl()).header(HttpHeaders.AUTHORIZATION, tokens.bearerFor(MEMBER_SUB)))
        .andExpect(status().isForbidden());

    invite(INVITEE_EMAIL, OrgRole.ORG_MEMBER, null, null);
    UUID invitationId = invitations.findAll().get(0).getId();
    mockMvc
        .perform(
            delete(invitationsUrl() + "/" + invitationId)
                .header(HttpHeaders.AUTHORIZATION, tokens.bearerFor(MEMBER_SUB)))
        .andExpect(status().isForbidden());
  }

  @Test
  void listsOutstandingAndSettledInvitationsForAdministrators() throws Exception {
    invite(INVITEE_EMAIL, OrgRole.ORG_MEMBER, team.getId(), TeamRole.TEAM_MEMBER);
    invite("second@example.org", OrgRole.ORG_ADMIN, null, null);

    mockMvc
        .perform(get(invitationsUrl()).header(HttpHeaders.AUTHORIZATION, tokens.bearerFor(ADMIN_SUB)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[?(@.email=='" + INVITEE_EMAIL + "')].teamName").value("Ward 5"));
  }

  @Test
  void neverPutsTheTokenInAnAdministratorFacingResponse() throws Exception {
    String created =
        mockMvc
            .perform(
                post(invitationsUrl())
                    .header(HttpHeaders.AUTHORIZATION, tokens.bearerFor(ADMIN_SUB))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body(INVITEE_EMAIL, OrgRole.ORG_MEMBER, null, null)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(created).doesNotContain(mail.lastToken());
    assertThat(created).doesNotContain("token");
  }

  @Test
  void stopsAnOrganisationSendingUnlimitedInvitations() throws Exception {
    // The configured ceiling is 50 an hour per organisation.
    for (int i = 0; i < 50; i++) {
      mockMvc
          .perform(
              post(invitationsUrl())
                  .header(HttpHeaders.AUTHORIZATION, tokens.bearerFor(ADMIN_SUB))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(body("bulk" + i + "@example.org", OrgRole.ORG_MEMBER, null, null)))
          .andExpect(status().isCreated());
    }

    mockMvc
        .perform(
            post(invitationsUrl())
                .header(HttpHeaders.AUTHORIZATION, tokens.bearerFor(ADMIN_SUB))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("one-too-many@example.org", OrgRole.ORG_MEMBER, null, null)))
        .andExpect(status().isConflict());
  }

  @Test
  void matchesTheInviteeRegardlessOfHowTheyCapitaliseTheirAddress() throws Exception {
    invite("Mixed.Case@Example.org", OrgRole.ORG_MEMBER, null, null);
    String token = mail.lastToken();
    AppUser invitee = signIn("sub-mixed", "mixed.case@example.org");

    accept(token, "sub-mixed", "mixed.case@example.org");

    assertThat(orgMemberships.find(invitee.getId(), org.getId())).isPresent();
  }

  @Test
  void recordsAnAuditEventForEveryInvitationChange() throws Exception {
    invite(INVITEE_EMAIL, OrgRole.ORG_MEMBER, null, null);
    signIn(INVITEE_SUB, INVITEE_EMAIL);
    accept(mail.lastToken(), INVITEE_SUB, INVITEE_EMAIL);

    invite("withdrawn@example.org", OrgRole.ORG_MEMBER, null, null);
    UUID toRevoke =
        invitations.findByOrgIdAndEmailAndStatus(
                org.getId(), "withdrawn@example.org", InvitationStatus.PENDING)
            .orElseThrow()
            .getId();
    mockMvc
        .perform(
            delete(invitationsUrl() + "/" + toRevoke)
                .header(HttpHeaders.AUTHORIZATION, tokens.bearerFor(ADMIN_SUB)))
        .andExpect(status().isNoContent());

    assertThat(auditEvents.findByOrgIdOrderByCreatedAtDesc(org.getId()))
        .extracting(event -> event.getAction())
        .containsExactlyInAnyOrder(
            "INVITATION_CREATED",
            "INVITATION_ACCEPTED",
            "INVITATION_CREATED",
            "INVITATION_REVOKED");
  }

  @Test
  void dropsTheRedisIndexWhenAnInvitationIsSettled() throws Exception {
    invite(INVITEE_EMAIL, OrgRole.ORG_MEMBER, null, null);
    String token = mail.lastToken();
    String redisKey = "invite:" + InvitationTokens.hash(token);

    assertThat(redis.hasKey(redisKey)).isTrue();
    assertThat(redis.getExpire(redisKey)).isGreaterThan(0);

    signIn(INVITEE_SUB, INVITEE_EMAIL);
    accept(token, INVITEE_SUB, INVITEE_EMAIL);

    assertThat(redis.hasKey(redisKey)).isFalse();
  }

  @Test
  void reportsAFailedInvitationEmailRatherThanAnUnexplainedError() throws Exception {
    mail.failWith(new EmailDeliveryException("SES said no", new RuntimeException()));

    mockMvc
        .perform(
            post(invitationsUrl())
                .header(HttpHeaders.AUTHORIZATION, tokens.bearerFor(ADMIN_SUB))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(INVITEE_EMAIL, OrgRole.ORG_MEMBER, null, null)))
        .andExpect(status().isServiceUnavailable())
        .andExpect(
            jsonPath("$.type").value("https://digitalhealth.app/problems/email-delivery-failed"))
        .andExpect(jsonPath("$.title").value("Email could not be sent"));

    assertThat(invitations.findAll())
        .as("an invitation nobody was told about is worse than none at all")
        .isEmpty();
    assertThat(redis.keys("invite:*"))
        .as("Redis must not index a token that was rolled back")
        .isEmpty();
  }

  @Test
  void leavesTheOutstandingInvitationIntactWhenReissuingFailsToSend() throws Exception {
    invite(INVITEE_EMAIL, OrgRole.ORG_MEMBER, null, null);
    String firstToken = mail.lastToken();
    String firstKey = "invite:" + InvitationTokens.hash(firstToken);

    mail.failWith(new EmailDeliveryException("SES said no", new RuntimeException()));
    mockMvc
        .perform(
            post(invitationsUrl())
                .header(HttpHeaders.AUTHORIZATION, tokens.bearerFor(ADMIN_SUB))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(INVITEE_EMAIL, OrgRole.ORG_ADMIN, null, null)))
        .andExpect(status().isServiceUnavailable());

    assertThat(invitations.findAll())
        .singleElement()
        .extracting(Invitation::getStatus)
        .as("the failed reissue must not withdraw the link already in the recipient's inbox")
        .isEqualTo(InvitationStatus.PENDING);
    assertThat(redis.hasKey(firstKey)).isTrue();
    mockMvc
        .perform(get("/api/v1/invitations/" + firstToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.valid").value(true));
  }

  @Test
  void refusesATeamRoleWithoutATeam() throws Exception {
    mockMvc
        .perform(
            post(invitationsUrl())
                .header(HttpHeaders.AUTHORIZATION, tokens.bearerFor(ADMIN_SUB))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(INVITEE_EMAIL, OrgRole.ORG_MEMBER, null, TeamRole.TEAM_ADMIN)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void refusesAnAddressThatIsNotAnAddress() throws Exception {
    mockMvc
        .perform(
            post(invitationsUrl())
                .header(HttpHeaders.AUTHORIZATION, tokens.bearerFor(ADMIN_SUB))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"not-an-address\",\"orgRole\":\"ORG_MEMBER\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.email").exists());
  }

  private String invitationsUrl() {
    return "/api/v1/orgs/" + org.getId() + "/invitations";
  }

  private void invite(String email, OrgRole orgRole, UUID teamId, TeamRole teamRole)
      throws Exception {
    mockMvc
        .perform(
            post(invitationsUrl())
                .header(HttpHeaders.AUTHORIZATION, tokens.bearerFor(ADMIN_SUB))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(email, orgRole, teamId, teamRole)))
        .andExpect(status().isCreated());
  }

  private void accept(String token, String sub, String email) throws Exception {
    mockMvc
        .perform(
            post("/api/v1/invitations/" + token + "/accept")
                .header(HttpHeaders.AUTHORIZATION, tokens.bearerFor(sub, email)))
        .andExpect(status().isNoContent());
  }

  /** Provisions the account the way a first sign-in would, and returns the resulting row. */
  private AppUser signIn(String sub, String email) throws Exception {
    mockMvc
        .perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, tokens.bearerFor(sub, email)))
        .andExpect(status().isOk());
    return users.findByEmail(email).orElseThrow();
  }

  private String body(String email, OrgRole orgRole, UUID teamId, TeamRole teamRole) {
    StringBuilder json = new StringBuilder("{\"email\":\"").append(email).append("\"");
    json.append(",\"orgRole\":\"").append(orgRole).append("\"");
    if (teamId != null) {
      json.append(",\"teamId\":\"").append(teamId).append("\"");
    }
    if (teamRole != null) {
      json.append(",\"teamRole\":\"").append(teamRole).append("\"");
    }
    return json.append("}").toString();
  }

  private AppUser user(String email, String sub) {
    return users.saveAndFlush(AppUser.builder().email(email).cognitoSub(sub).build());
  }
}
