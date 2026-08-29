package io.simplicity.training.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.simplicity.training.model.entity.AppUser;
import io.simplicity.training.model.enums.OrgRole;
import io.simplicity.training.support.AbstractIntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/** The journey a clinician takes from their very first sign-in to a usable account. */
class OnboardingTest extends AbstractIntegrationTest {

  private static final String SUB = "sub-onboarding";
  private static final String EMAIL = "new@example.org";

  @Test
  void takesASelfSignedUpClinicianFromNothingToAdministeringTheirOwnOrganisation()
      throws Exception {
    String bearer = "Bearer " + tokens.accessTokenFor(SUB, Map.of("email", EMAIL));

    // 1. First call provisions the account and tells the client to show the wizard.
    mockMvc
        .perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, bearer))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.profileCompleted").value(false))
        .andExpect(jsonPath("$.organisations").isEmpty());

    // 2. The wizard submits their professional details.
    mockMvc
        .perform(
            put("/api/v1/me/profile")
                .header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"fullName":"Dr Ada Lovelace","phone":"+64 21 555 0100",
                     "professionalRole":"Clinical Psychologist"}"""))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.profileCompleted").value(true))
        .andExpect(jsonPath("$.fullName").value("Dr Ada Lovelace"));

    // 3. Having no invitation, they create their own organisation.
    mockMvc
        .perform(
            post("/api/v1/organisations")
                .header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name":"North Shore Hospital","organisationType":"HOSPITAL","country":"NZ"}"""))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.slug").value("north-shore-hospital"));

    // 4. Onboarding is over, and they administer what they created.
    mockMvc
        .perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, bearer))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.profileCompleted").value(true))
        .andExpect(jsonPath("$.organisations.length()").value(1))
        .andExpect(jsonPath("$.organisations[0].orgRole").value("ORG_ADMIN"));
  }

  @Test
  void makesTheCreatorAnAdministratorAndRecordsIt() throws Exception {
    String bearer = "Bearer " + tokens.accessTokenFor(SUB, Map.of("email", EMAIL));
    mockMvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, bearer));

    mockMvc
        .perform(
            post("/api/v1/organisations")
                .header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Audited Clinic\",\"organisationType\":\"CLINIC\"}"))
        .andExpect(status().isCreated());

    AppUser creator = users.findByCognitoSub(SUB).orElseThrow();
    var organisation = organisations.findBySlug("audited-clinic").orElseThrow();

    assertThat(orgMemberships.find(creator.getId(), organisation.getId()).orElseThrow().getOrgRole())
        .isEqualTo(OrgRole.ORG_ADMIN);
    assertThat(auditEvents.findByOrgIdOrderByCreatedAtDesc(organisation.getId()))
        .extracting(event -> event.getAction())
        .containsExactly("ORGANISATION_CREATED");
  }

  @Test
  void givesTwoOrganisationsOfTheSameNameDistinctSlugs() throws Exception {
    createOrganisationAs("sub-one", "one@example.org", "City Clinic");
    createOrganisationAs("sub-two", "two@example.org", "City Clinic");

    assertThat(organisations.findBySlug("city-clinic")).isPresent();
    assertThat(organisations.findBySlug("city-clinic-2")).isPresent();
  }

  @Test
  void buildsAUsableSlugFromAccentedAndPunctuatedNames() throws Exception {
    createOrganisationAs("sub-accent", "accent@example.org", "Hôpital Saint-Étienne (North)");

    assertThat(organisations.findBySlug("hopital-saint-etienne-north")).isPresent();
  }

  @Test
  void refusesANameWithNothingToMakeASlugFrom() throws Exception {
    String bearer = signedIn("sub-blank", "blank@example.org");

    mockMvc
        .perform(
            post("/api/v1/organisations")
                .header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"!!!\",\"organisationType\":\"OTHER\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void refusesAProfileWithNoName() throws Exception {
    String bearer = signedIn(SUB, EMAIL);

    mockMvc
        .perform(
            put("/api/v1/me/profile")
                .header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\":\"  \",\"professionalRole\":\"Nurse\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.fullName").exists());
  }

  @Test
  void refusesAProfileWithAnImplausiblePhoneNumber() throws Exception {
    String bearer = signedIn(SUB, EMAIL);

    mockMvc
        .perform(
            put("/api/v1/me/profile")
                .header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"fullName":"Ada","phone":"not a phone","professionalRole":"Nurse"}"""))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.phone").exists());
  }

  @Test
  void acceptsAProfileWithNoPhoneNumberAtAll() throws Exception {
    String bearer = signedIn(SUB, EMAIL);

    mockMvc
        .perform(
            put("/api/v1/me/profile")
                .header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\":\"Ada\",\"professionalRole\":\"Nurse\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.phone").doesNotExist());
  }

  @Test
  void reflectsTheCompletedProfileImmediatelyDespiteTheCachedPrincipal() throws Exception {
    String bearer = signedIn(SUB, EMAIL);

    mockMvc
        .perform(
            put("/api/v1/me/profile")
                .header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\":\"Ada\",\"professionalRole\":\"Nurse\"}"))
        .andExpect(status().isOk());

    // A stale cached principal here would send the clinician straight back to the wizard.
    mockMvc
        .perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, bearer))
        .andExpect(jsonPath("$.profileCompleted").value(true));
  }

  @Test
  void refusesToCreateAnOrganisationWithoutAToken() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/organisations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Sneaky\",\"organisationType\":\"OTHER\"}"))
        .andExpect(status().isUnauthorized());
  }

  private String signedIn(String sub, String email) throws Exception {
    String bearer = "Bearer " + tokens.accessTokenFor(sub, Map.of("email", email));
    mockMvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, bearer));
    return bearer;
  }

  private void createOrganisationAs(String sub, String email, String name) throws Exception {
    String bearer = signedIn(sub, email);
    mockMvc
        .perform(
            post("/api/v1/organisations")
                .header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + name + "\",\"organisationType\":\"CLINIC\"}"))
        .andExpect(status().isCreated());
  }
}
