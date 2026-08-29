package io.simplicity.training.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.simplicity.training.model.entity.AppUser;
import io.simplicity.training.model.entity.OrgMembership;
import io.simplicity.training.model.entity.Organisation;
import io.simplicity.training.model.enums.OrgRole;
import io.simplicity.training.model.enums.OrganisationType;
import io.simplicity.training.service.assistant.ModuleIndexer;
import io.simplicity.training.support.AbstractIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/**
 * The assistant, which answers from the training content or does not answer.
 *
 * <p>The assertions that matter are the refusals: a question the material does not cover must not
 * reach the model at all, and retrieval must not cross an organisation, an archive, or a draft.
 */
class AssistantTest extends AbstractIntegrationTest {

  private static final ObjectMapper JSON = new ObjectMapper();
  private static final String ADMIN = "assistant-admin";

  @Autowired private ModuleIndexer indexer;

  private Organisation clinic;

  @BeforeEach
  void setUpOrganisation() {
    clinic =
        organisations.saveAndFlush(
            Organisation.builder()
                .name("Clinic")
                .slug("clinic-" + UUID.randomUUID().toString().substring(0, 6))
                .organisationType(OrganisationType.CLINIC)
                .build());
    AppUser admin =
        users
            .findByCognitoSub(ADMIN)
            .orElseGet(
                () ->
                    users.saveAndFlush(
                        AppUser.builder()
                            .email(ADMIN + "@example.org")
                            .cognitoSub(ADMIN)
                            .fullName("Author")
                            .profileCompleted(true)
                            .build()));
    orgMemberships.saveAndFlush(OrgMembership.of(admin.getId(), clinic.getId(), OrgRole.ORG_ADMIN));
  }

  @Test
  void answersFromThePublishedModuleAndCitesIt() throws Exception {
    publishModule("Grounding techniques",
        "Grounding techniques help a client return attention to the present moment.");
    indexer.indexPublished();

    JsonNode answer = ask("What are grounding techniques?");

    assertThat(answer.get("answered").asBoolean()).isTrue();
    assertThat(answer.get("citations").get(0).get("moduleTitle").asText())
        .isEqualTo("Grounding techniques");
  }

  @Test
  void declinesWhatTheTrainingDoesNotCoverWithoutCallingTheModel() throws Exception {
    publishModule("Grounding techniques", "Grounding helps a client return to the present.");
    indexer.indexPublished();

    JsonNode answer = ask("What is the correct dose of quetiapine for insomnia?");

    assertThat(answer.get("answered").asBoolean()).isFalse();
    assertThat(answer.get("answer").asText()).contains("supervisor");
    assertThat(answerGenerator.calls)
        .as("an unanswerable question must not reach the model at all")
        .isEmpty();
  }

  @Test
  void retrievalDoesNotCrossOrganisations() throws Exception {
    publishModule("Grounding techniques", "Grounding helps a client return to the present.");
    indexer.indexPublished();

    Organisation other =
        organisations.saveAndFlush(
            Organisation.builder()
                .name("Other")
                .slug("other-" + UUID.randomUUID().toString().substring(0, 6))
                .organisationType(OrganisationType.CLINIC)
                .build());
    AppUser outsider =
        users.saveAndFlush(
            AppUser.builder()
                .email("outsider@example.org")
                .cognitoSub("assistant-outsider")
                .profileCompleted(true)
                .build());
    orgMemberships.saveAndFlush(OrgMembership.of(outsider.getId(), other.getId(), OrgRole.ORG_ADMIN));

    JsonNode answer =
        JSON.readTree(
            mockMvc
                .perform(
                    post("/api/v1/orgs/{orgId}/assistant/questions", other.getId())
                        .header(
                            HttpHeaders.AUTHORIZATION,
                            tokens.bearerFor("assistant-outsider", "outsider@example.org"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"What are grounding techniques?\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());

    assertThat(answer.get("answered").asBoolean())
        .as("another organisation's training is not theirs to retrieve")
        .isFalse();
  }

  @Test
  void anUnpublishedDraftIsNotRetrievable() throws Exception {
    UUID moduleId = createModule("Work in progress");
    putSections(moduleId, "Draft section", "Grounding helps a client return to the present.");
    // Never published, so nothing to index.
    indexer.indexPublished();

    assertThat(moduleChunks.count()).isZero();
    assertThat(ask("What are grounding techniques?").get("answered").asBoolean()).isFalse();
  }

  @Test
  void anArchivedModuleStopsBeingRetrievable() throws Exception {
    UUID moduleId = publishModule("Grounding techniques",
        "Grounding techniques help a client return attention to the present moment.");
    indexer.indexPublished();
    assertThat(ask("What are grounding techniques?").get("answered").asBoolean()).isTrue();

    mockMvc
        .perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .delete("/api/v1/orgs/{orgId}/modules/{moduleId}", clinic.getId(), moduleId)
                .header(HttpHeaders.AUTHORIZATION, bearer()))
        .andExpect(status().isNoContent());

    assertThat(ask("What are grounding techniques?").get("answered").asBoolean())
        .as("an archive is meant to be unreachable, however good the similarity")
        .isFalse();
  }

  @Test
  void republishingReplacesWhatIsRetrieved() throws Exception {
    UUID moduleId = publishModule("Protocol", "The protocol begins with a listening exercise.");
    indexer.indexPublished();

    mockMvc
        .perform(
            post("/api/v1/orgs/{orgId}/modules/{moduleId}/draft", clinic.getId(), moduleId)
                .header(HttpHeaders.AUTHORIZATION, bearer()))
        .andExpect(status().isOk());
    putSections(moduleId, "Protocol", "The protocol now begins with a breathing exercise.");
    publish(moduleId);
    indexer.indexPublished();

    JsonNode answer = ask("What does the protocol begin with?");
    assertThat(answer.get("answered").asBoolean()).isTrue();
    assertThat(answerGenerator.calls.get(answerGenerator.calls.size() - 1).get(0).content())
        .as("the superseded version must stop being what is retrieved")
        .contains("breathing");
  }

  @Test
  void aQuestionRequiresSomeText() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/orgs/{orgId}/assistant/questions", clinic.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"  \"}"))
        .andExpect(status().isBadRequest());
  }

  // -----------------------------------------------------------------------------------------

  private JsonNode ask(String question) throws Exception {
    return JSON.readTree(
        mockMvc
            .perform(
                post("/api/v1/orgs/{orgId}/assistant/questions", clinic.getId())
                    .header(HttpHeaders.AUTHORIZATION, bearer())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(JSON.writeValueAsString(java.util.Map.of("question", question))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  private UUID publishModule(String title, String body) throws Exception {
    UUID moduleId = createModule(title);
    putSections(moduleId, title, body);
    publish(moduleId);
    return moduleId;
  }

  private UUID createModule(String title) throws Exception {
    return UUID.fromString(
        JSON.readTree(
                mockMvc
                    .perform(
                        post("/api/v1/orgs/{orgId}/modules", clinic.getId())
                            .header(HttpHeaders.AUTHORIZATION, bearer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"title\":\"" + title + "\"}"))
                    .andExpect(status().isCreated())
                    .andReturn()
                    .getResponse()
                    .getContentAsString())
            .get("moduleId")
            .asText());
  }

  private void putSections(UUID moduleId, String title, String body) throws Exception {
    mockMvc
        .perform(
            put("/api/v1/orgs/{orgId}/modules/{moduleId}/draft/sections", clinic.getId(), moduleId)
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    JSON.writeValueAsString(
                        java.util.Map.of(
                            "sections",
                            java.util.List.of(java.util.Map.of("title", title, "body", body))))))
        .andExpect(status().isOk());
  }

  private void publish(UUID moduleId) throws Exception {
    mockMvc
        .perform(
            post("/api/v1/orgs/{orgId}/modules/{moduleId}/draft/publish", clinic.getId(), moduleId)
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"supersedesCompletions\":false}"))
        .andExpect(status().isOk());
  }

  private String bearer() {
    return tokens.bearerFor(ADMIN, ADMIN + "@example.org");
  }
}
