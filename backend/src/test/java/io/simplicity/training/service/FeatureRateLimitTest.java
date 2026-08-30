package io.simplicity.training.service;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.simplicity.training.model.entity.AppUser;
import io.simplicity.training.model.entity.OrgMembership;
import io.simplicity.training.model.entity.Organisation;
import io.simplicity.training.model.enums.OrgRole;
import io.simplicity.training.model.enums.OrganisationType;
import io.simplicity.training.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/**
 * The limits the specifications describe, on the endpoints that had none.
 *
 * <p>{@code MediaService.register} hands out a presigned URL for a half-gigabyte object and was
 * unlimited, while the Phase 5 iOS specification stated the limit was "enforced server-side
 * already".
 */
class FeatureRateLimitTest extends AbstractIntegrationTest {

  private static final String ADMIN_SUB = "sub-limit-admin";
  private static final String OTHER_SUB = "sub-limit-other";

  private Organisation organisation;

  @BeforeEach
  void seedAnOrganisation() {
    organisation =
        organisations.saveAndFlush(
            Organisation.builder()
                .name("limit-clinic")
                .slug("limit-clinic")
                .organisationType(OrganisationType.CLINIC)
                .build());

    AppUser admin =
        users.saveAndFlush(
            AppUser.builder().email("limit-admin@example.org").cognitoSub(ADMIN_SUB).build());
    AppUser other =
        users.saveAndFlush(
            AppUser.builder().email("limit-other@example.org").cognitoSub(OTHER_SUB).build());

    orgMemberships.saveAndFlush(
        OrgMembership.of(admin.getId(), organisation.getId(), OrgRole.ORG_ADMIN));
    orgMemberships.saveAndFlush(
        OrgMembership.of(other.getId(), organisation.getId(), OrgRole.ORG_ADMIN));
  }

  @Test
  void refusesASecondUploadRegistrationWithinTheMinute() throws Exception {
    registerAnUpload(ADMIN_SUB).andExpect(status().isCreated());

    registerAnUpload(ADMIN_SUB).andExpect(status().isConflict());
  }

  @Test
  void countsUploadRegistrationsPerUserRatherThanGlobally() throws Exception {
    registerAnUpload(ADMIN_SUB).andExpect(status().isCreated());

    registerAnUpload(OTHER_SUB).andExpect(status().isCreated());
  }

  @Test
  void allowsReflectionWritesWellBeyondAnyHonestDay() throws Exception {
    for (int i = 0; i < 20; i++) {
      writeAReflection().andExpect(status().isCreated());
    }
  }

  private org.springframework.test.web.servlet.ResultActions registerAnUpload(String sub)
      throws Exception {
    return mockMvc.perform(
        post("/api/v1/orgs/" + organisation.getId() + "/media")
            .header(HttpHeaders.AUTHORIZATION, tokens.bearerFor(sub))
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                {"filename":"a.mp4","contentType":"video/mp4","sizeBytes":1024}
                """));
  }

  private org.springframework.test.web.servlet.ResultActions writeAReflection() throws Exception {
    return mockMvc.perform(
        post("/api/v1/me/reflections")
            .header(HttpHeaders.AUTHORIZATION, tokens.bearerFor(ADMIN_SUB))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"title\":\"A day\",\"body\":\"Something worth remembering.\"}"));
  }
}
