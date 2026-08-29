package io.simplicity.training.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.simplicity.training.model.entity.AppUser;
import io.simplicity.training.model.entity.OrgMembership;
import io.simplicity.training.model.entity.Organisation;
import io.simplicity.training.model.entity.Team;
import io.simplicity.training.model.entity.TeamMember;
import io.simplicity.training.model.enums.OrgRole;
import io.simplicity.training.model.enums.OrganisationType;
import io.simplicity.training.model.enums.TeamRole;
import io.simplicity.training.support.AbstractIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/**
 * The quiz, which is what makes completion mean more than a clinician saying so.
 *
 * <p>Retakes are unlimited and feedback is full, so this is mastery learning rather than
 * assessment. The assertions worth having are that marking happens on the server and that the
 * answer never reaches a learner who has not submitted.
 */
class QuizTest extends AbstractIntegrationTest {

  private static final ObjectMapper JSON = new ObjectMapper();
  private static final String ADMIN = "quiz-admin";

  private Organisation clinic;
  private Team team;
  private UUID moduleId;

  @BeforeEach
  void setUpModule() throws Exception {
    clinic =
        organisations.saveAndFlush(
            Organisation.builder()
                .name("Clinic")
                .slug("clinic-" + UUID.randomUUID().toString().substring(0, 6))
                .organisationType(OrganisationType.CLINIC)
                .build());
    AppUser admin =
        users.saveAndFlush(
            AppUser.builder()
                .email("quiz-admin@example.org")
                .cognitoSub(ADMIN)
                .fullName("Author")
                .profileCompleted(true)
                .build());
    orgMemberships.saveAndFlush(OrgMembership.of(admin.getId(), clinic.getId(), OrgRole.ORG_ADMIN));
    team = teams.saveAndFlush(Team.builder().orgId(clinic.getId()).name("Ward").build());
    teamMembers.saveAndFlush(TeamMember.of(team.getId(), admin.getId(), TeamRole.TEAM_ADMIN));

    moduleId = UUID.fromString(postJson("/api/v1/orgs/" + clinic.getId() + "/modules",
        "{\"title\":\"Protocol\"}").get("moduleId").asText());
    putJson("/api/v1/orgs/" + clinic.getId() + "/modules/" + moduleId + "/draft/sections",
        "{\"sections\":[{\"title\":\"Only section\",\"body\":\"Read this.\"}]}");
  }

  @Test
  void theLearnerNeverSeesWhichOptionIsCorrect() throws Exception {
    withQuiz();
    publish();
    assignToTeam();

    MvcResult result =
        mockMvc
            .perform(get("/api/v1/orgs/{orgId}/learning/{moduleId}/quiz", clinic.getId(), moduleId)
                .header(HttpHeaders.AUTHORIZATION, bearer()))
            .andExpect(status().isOk())
            .andReturn();

    // Asserted against the serialised body rather than an object, because the failure this guards
    // against is a field being serialised that nobody meant to send.
    String body = result.getResponse().getContentAsString();
    assertThat(body).doesNotContain("correct");
    assertThat(body).doesNotContain("explanation");
    assertThat(body).contains("Which comes first?");
  }

  @Test
  void marksTheAttemptOnTheServer() throws Exception {
    withQuiz();
    publish();
    assignToTeam();

    JsonNode quiz = getJson("/api/v1/orgs/" + clinic.getId() + "/learning/" + moduleId + "/quiz");
    String questionId = quiz.get("questions").get(0).get("questionId").asText();
    String wrongOption = quiz.get("questions").get(0).get("options").get(1).get("optionId").asText();

    JsonNode result =
        postJson("/api/v1/orgs/" + clinic.getId() + "/learning/" + moduleId + "/quiz/attempts",
            "{\"answers\":[{\"questionId\":\"" + questionId + "\",\"optionId\":\"" + wrongOption + "\"}]}");

    assertThat(result.get("passed").asBoolean()).isFalse();
    assertThat(result.get("correctCount").asInt()).isZero();
    assertThat(result.get("questions").get(0).get("explanation").asText())
        .as("a wrong answer has to teach something, or retrying is just guessing again")
        .isEqualTo("Assessment always precedes intervention.");
  }

  @Test
  void everySectionReadIsNotEnoughWhileTheQuizIsUnpassed() throws Exception {
    withQuiz();
    publish();
    assignToTeam();
    readEverySection();

    assertThat(learningStatus()).isEqualTo("IN_PROGRESS");
  }

  @Test
  void passingCompletesTheModule() throws Exception {
    withQuiz();
    publish();
    assignToTeam();
    readEverySection();

    answerCorrectly();

    assertThat(learningStatus()).isEqualTo("COMPLETED");
  }

  @Test
  void attemptsAccumulate() throws Exception {
    withQuiz();
    publish();
    assignToTeam();

    JsonNode quiz = getJson("/api/v1/orgs/" + clinic.getId() + "/learning/" + moduleId + "/quiz");
    String questionId = quiz.get("questions").get(0).get("questionId").asText();
    String wrong = quiz.get("questions").get(0).get("options").get(1).get("optionId").asText();
    String answers = "{\"answers\":[{\"questionId\":\"" + questionId + "\",\"optionId\":\"" + wrong + "\"}]}";

    postJson("/api/v1/orgs/" + clinic.getId() + "/learning/" + moduleId + "/quiz/attempts", answers);
    JsonNode second =
        postJson("/api/v1/orgs/" + clinic.getId() + "/learning/" + moduleId + "/quiz/attempts", answers);

    assertThat(second.get("attemptNumber").asInt())
        .as("how many attempts it took is the only signal this model leaves behind")
        .isEqualTo(2);
  }

  @Test
  void aQuestionWithNoCorrectOptionCannotBePublished() throws Exception {
    putJson(
        "/api/v1/orgs/" + clinic.getId() + "/modules/" + moduleId + "/draft/quiz",
        "{\"questions\":[{\"prompt\":\"Unanswerable\",\"options\":["
            + "{\"label\":\"A\",\"correct\":false},{\"label\":\"B\",\"correct\":false}]}]}");

    mockMvc
        .perform(
            post("/api/v1/orgs/{orgId}/modules/{moduleId}/draft/publish", clinic.getId(), moduleId)
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"supersedesCompletions\":false}"))
        .andExpect(status().isConflict());
  }

  @Test
  void aModuleWithoutAQuizStillCompletesOnItsSections() throws Exception {
    publish();
    assignToTeam();
    readEverySection();

    assertThat(learningStatus()).isEqualTo("COMPLETED");
  }

  // -----------------------------------------------------------------------------------------

  private void withQuiz() throws Exception {
    putJson(
        "/api/v1/orgs/" + clinic.getId() + "/modules/" + moduleId + "/draft/quiz",
        "{\"questions\":[{\"prompt\":\"Which comes first?\","
            + "\"explanation\":\"Assessment always precedes intervention.\","
            + "\"options\":[{\"label\":\"Assessment\",\"correct\":true},"
            + "{\"label\":\"Intervention\",\"correct\":false}]}]}");
  }

  private void answerCorrectly() throws Exception {
    JsonNode quiz = getJson("/api/v1/orgs/" + clinic.getId() + "/learning/" + moduleId + "/quiz");
    StringBuilder answers = new StringBuilder("{\"answers\":[");
    boolean first = true;
    for (JsonNode question : quiz.get("questions")) {
      // The learner payload does not say which is right, so the test asks the author's view.
      JsonNode authored = getJson("/api/v1/orgs/" + clinic.getId() + "/modules/" + moduleId);
      for (JsonNode candidate : authored.get("published").get("questions")) {
        if (!candidate.get("questionId").asText().equals(question.get("questionId").asText())) {
          continue;
        }
        for (JsonNode option : candidate.get("options")) {
          if (option.get("correct").asBoolean()) {
            answers
                .append(first ? "" : ",")
                .append("{\"questionId\":\"")
                .append(question.get("questionId").asText())
                .append("\",\"optionId\":\"")
                .append(option.get("optionId").asText())
                .append("\"}");
            first = false;
          }
        }
      }
    }
    answers.append("]}");
    postJson("/api/v1/orgs/" + clinic.getId() + "/learning/" + moduleId + "/quiz/attempts", answers.toString());
  }

  private void readEverySection() throws Exception {
    JsonNode module = getJson("/api/v1/orgs/" + clinic.getId() + "/learning/" + moduleId);
    for (JsonNode section : module.get("sections")) {
      mockMvc
          .perform(
              put("/api/v1/orgs/{orgId}/learning/sections/{sectionId}/complete",
                      clinic.getId(), section.get("sectionId").asText())
                  .header(HttpHeaders.AUTHORIZATION, bearer()))
          .andExpect(status().isOk());
    }
  }

  private String learningStatus() throws Exception {
    for (JsonNode module : getJson("/api/v1/orgs/" + clinic.getId() + "/learning")) {
      if (module.get("moduleId").asText().equals(moduleId.toString())) {
        return module.get("status").asText();
      }
    }
    return "ABSENT";
  }

  private void publish() throws Exception {
    postJson("/api/v1/orgs/" + clinic.getId() + "/modules/" + moduleId + "/draft/publish",
        "{\"supersedesCompletions\":false}");
  }

  private void assignToTeam() throws Exception {
    putJson("/api/v1/orgs/" + clinic.getId() + "/modules/" + moduleId + "/teams",
        "{\"teamIds\":[\"" + team.getId() + "\"]}");
  }

  private JsonNode postJson(String path, String body) throws Exception {
    return JSON.readTree(
        mockMvc
            .perform(
                post(path)
                    .header(HttpHeaders.AUTHORIZATION, bearer())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  private void putJson(String path, String body) throws Exception {
    mockMvc
        .perform(
            put(path)
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk());
  }

  private JsonNode getJson(String path) throws Exception {
    return JSON.readTree(
        mockMvc
            .perform(get(path).header(HttpHeaders.AUTHORIZATION, bearer()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  private String bearer() {
    return tokens.bearerFor(ADMIN, "quiz-admin@example.org");
  }
}
