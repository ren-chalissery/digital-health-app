package io.simplicity.training.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import io.simplicity.training.service.media.MediaConvertPoller;
import io.simplicity.training.support.AbstractIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/**
 * Video: upload, transcode, and who is allowed to watch.
 *
 * <p>A presigned URL is a bearer credential for as long as it lives, so the assertions that matter
 * are about who can obtain one.
 */
class MediaTest extends AbstractIntegrationTest {

  private static final ObjectMapper JSON = new ObjectMapper();
  private static final String ADMIN = "media-admin";
  private static final String LEARNER = "media-learner";

  @Autowired private MediaConvertPoller poller;

  private Organisation clinic;
  private Team ward;
  private AppUser learner;

  @BeforeEach
  void setUpOrganisation() {
    clinic =
        organisations.saveAndFlush(
            Organisation.builder()
                .name("Clinic")
                .slug("clinic-" + UUID.randomUUID().toString().substring(0, 6))
                .organisationType(OrganisationType.CLINIC)
                .build());
    AppUser admin = user(ADMIN);
    orgMemberships.saveAndFlush(OrgMembership.of(admin.getId(), clinic.getId(), OrgRole.ORG_ADMIN));
    learner = user(LEARNER);
    orgMemberships.saveAndFlush(OrgMembership.of(learner.getId(), clinic.getId(), OrgRole.ORG_MEMBER));
    ward = teams.saveAndFlush(Team.builder().orgId(clinic.getId()).name("Ward").build());
  }

  @Test
  void registeringAnUploadReturnsSomewhereToPutIt() throws Exception {
    JsonNode registered = register("briefing.mp4", "video/mp4", 10_000_000L);

    assertThat(registered.get("uploadUrl").asText()).contains("upload=1");
    assertThat(objectStore.presignedPuts)
        .as("the key is scoped to the organisation and the asset, not chosen by the client")
        .anyMatch(key -> key.startsWith(clinic.getId() + "/"));
  }

  @Test
  void refusesAFileTypeThatIsNotVideo() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/orgs/{orgId}/media", clinic.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(ADMIN))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"filename\":\"payroll.xlsx\",\"contentType\":"
                    + "\"application/vnd.ms-excel\",\"sizeBytes\":1000}"))
        .andExpect(status().isBadRequest());

    assertThat(objectStore.presignedPuts)
        .as("a rejected type must never be handed a place to upload to")
        .isEmpty();
  }

  @Test
  void refusesSomethingLargerThanTheCap() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/orgs/{orgId}/media", clinic.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(ADMIN))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"filename\":\"huge.mp4\",\"contentType\":\"video/mp4\","
                    + "\"sizeBytes\":9000000000}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void theTranscodeIsSubmittedOnlyOnceTheUploadIsReported() throws Exception {
    UUID assetId = UUID.fromString(register("v.mp4", "video/mp4", 100L).get("assetId").asText());
    assertThat(transcoder.submitted).isEmpty();

    mockMvc
        .perform(
            post("/api/v1/orgs/{orgId}/media/{assetId}/uploaded", clinic.getId(), assetId)
                .header(HttpHeaders.AUTHORIZATION, bearer(ADMIN)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("PROCESSING"));

    assertThat(transcoder.submitted).hasSize(1);
  }

  @Test
  void thePollerMovesAFinishedJobToReady() throws Exception {
    UUID assetId = uploadedAsset();
    transcoder.finish("job-0", 245);

    poller.poll();

    assertThat(mediaAssets.findById(assetId).orElseThrow().getStatus().name()).isEqualTo("READY");
    assertThat(mediaAssets.findById(assetId).orElseThrow().getDurationSeconds()).isEqualTo(245);
  }

  @Test
  void aFailedTranscodeSaysWhy() throws Exception {
    UUID assetId = uploadedAsset();
    transcoder.fail("job-0", "Unsupported audio codec");

    poller.poll();

    assertThat(mediaAssets.findById(assetId).orElseThrow().getStatus().name()).isEqualTo("FAILED");
    assertThat(mediaAssets.findById(assetId).orElseThrow().getFailureReason())
        .as("a video that silently does nothing reads as a broken product")
        .isEqualTo("Unsupported audio codec");
  }

  @Test
  void anAssignedClinicianGetsAPlaybackUrl() throws Exception {
    UUID assetId = readyAssetInAssignedModule();

    mockMvc
        .perform(
            get("/api/v1/orgs/{orgId}/learning/media/{assetId}/playback", clinic.getId(), assetId)
                .header(HttpHeaders.AUTHORIZATION, bearer(LEARNER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.url").exists())
        .andExpect(jsonPath("$.expiresInSeconds").value(900));
  }

  @Test
  void aClinicianWithoutTheAssignmentGetsNothing() throws Exception {
    UUID assetId = readyAssetInAssignedModule();
    // Out of the team, so the module is no longer theirs.
    teamMembers.deleteAllInBatch();

    mockMvc
        .perform(
            get("/api/v1/orgs/{orgId}/learning/media/{assetId}/playback", clinic.getId(), assetId)
                .header(HttpHeaders.AUTHORIZATION, bearer(LEARNER)))
        .andExpect(status().isForbidden());
  }

  @Test
  void aVideoStillProcessingHasNoUrl() throws Exception {
    UUID assetId = uploadedAsset();

    mockMvc
        .perform(
            get("/api/v1/orgs/{orgId}/learning/media/{assetId}/playback", clinic.getId(), assetId)
                .header(HttpHeaders.AUTHORIZATION, bearer(ADMIN)))
        .andExpect(status().isConflict());
  }

  @Test
  void deletingAVideoEmptiesTheSectionsThatUsedIt() throws Exception {
    UUID assetId = readyAssetInAssignedModule();
    assertThat(moduleSections.findByMediaAssetId(assetId)).hasSize(1);

    mockMvc
        .perform(
            delete("/api/v1/orgs/{orgId}/media/{assetId}", clinic.getId(), assetId)
                .header(HttpHeaders.AUTHORIZATION, bearer(ADMIN)))
        .andExpect(status().isNoContent());

    assertThat(moduleSections.findByMediaAssetId(assetId)).isEmpty();
    assertThat(moduleSections.findAll())
        .as("the writing around the video stays; only the video goes")
        .hasSize(1);
    assertThat(objectStore.deleted).isNotEmpty();
  }

  @Test
  void aCaptionTrackIsStoredAndOfferedAlongsideTheVideo() throws Exception {
    UUID assetId = readyAssetInAssignedModule();

    mockMvc
        .perform(
            put("/api/v1/orgs/{orgId}/media/{assetId}/captions", clinic.getId(), assetId)
                .header(HttpHeaders.AUTHORIZATION, bearer(ADMIN))
                .contentType("text/vtt")
                .content("WEBVTT\n\n00:00.000 --> 00:02.000\nWelcome.\n"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.hasCaptions").value(true));

    assertThat(objectStore.stored.keySet()).anyMatch(key -> key.endsWith("captions.vtt"));

    mockMvc
        .perform(
            get("/api/v1/orgs/{orgId}/learning/media/{assetId}/playback", clinic.getId(), assetId)
                .header(HttpHeaders.AUTHORIZATION, bearer(LEARNER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.captionUrl").exists());
  }

  /** Alone among the write endpoints, this one accepted a body of any size at all. */
  @Test
  void refusesACaptionFileFarLargerThanAnyRealOne() throws Exception {
    UUID assetId = uploadedAsset();
    String enormous = "WEBVTT\n\n" + "x".repeat(3 * 1024 * 1024);

    mockMvc
        .perform(
            put("/api/v1/orgs/{orgId}/media/{assetId}/captions", clinic.getId(), assetId)
                .header(HttpHeaders.AUTHORIZATION, bearer(ADMIN))
                .contentType("text/vtt")
                .content(enormous))
        .andExpect(status().isBadRequest());
  }

  @Test
  void refusesSomethingThatIsNotWebVtt() throws Exception {
    UUID assetId = uploadedAsset();

    // An SRT is what somebody will actually try, and a browser ignores it in silence.
    mockMvc
        .perform(
            put("/api/v1/orgs/{orgId}/media/{assetId}/captions", clinic.getId(), assetId)
                .header(HttpHeaders.AUTHORIZATION, bearer(ADMIN))
                .contentType("text/vtt")
                .content("1\n00:00:00,000 --> 00:00:02,000\nWelcome.\n"))
        .andExpect(status().isBadRequest());

    assertThat(objectStore.stored).isEmpty();
  }

  @Test
  void aVideoWithoutCaptionsSaysSoRatherThanFailing() throws Exception {
    UUID assetId = readyAssetInAssignedModule();

    mockMvc
        .perform(
            get("/api/v1/orgs/{orgId}/learning/media/{assetId}/playback", clinic.getId(), assetId)
                .header(HttpHeaders.AUTHORIZATION, bearer(LEARNER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.url").exists())
        .andExpect(jsonPath("$.captionUrl").doesNotExist());
  }

  @Test
  void removingCaptionsLeavesTheVideoPlayable() throws Exception {
    UUID assetId = readyAssetInAssignedModule();
    mockMvc
        .perform(
            put("/api/v1/orgs/{orgId}/media/{assetId}/captions", clinic.getId(), assetId)
                .header(HttpHeaders.AUTHORIZATION, bearer(ADMIN))
                .contentType("text/vtt")
                .content("WEBVTT\n\n00:00.000 --> 00:01.000\nHello.\n"))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            delete("/api/v1/orgs/{orgId}/media/{assetId}/captions", clinic.getId(), assetId)
                .header(HttpHeaders.AUTHORIZATION, bearer(ADMIN)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.hasCaptions").value(false));

    mockMvc
        .perform(
            get("/api/v1/orgs/{orgId}/learning/media/{assetId}/playback", clinic.getId(), assetId)
                .header(HttpHeaders.AUTHORIZATION, bearer(LEARNER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.url").exists());
  }

  @Test
  void anOrdinaryMemberCannotUpload() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/orgs/{orgId}/media", clinic.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(LEARNER))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"filename\":\"v.mp4\",\"contentType\":\"video/mp4\",\"sizeBytes\":10}"))
        .andExpect(status().isForbidden());
  }

  // -----------------------------------------------------------------------------------------

  private JsonNode register(String filename, String contentType, long size) throws Exception {
    return JSON.readTree(
        mockMvc
            .perform(
                post("/api/v1/orgs/{orgId}/media", clinic.getId())
                    .header(HttpHeaders.AUTHORIZATION, bearer(ADMIN))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"filename\":\"" + filename + "\",\"contentType\":\"" + contentType
                        + "\",\"sizeBytes\":" + size + "}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  private UUID uploadedAsset() throws Exception {
    UUID assetId = UUID.fromString(register("v.mp4", "video/mp4", 100L).get("assetId").asText());
    mockMvc
        .perform(
            post("/api/v1/orgs/{orgId}/media/{assetId}/uploaded", clinic.getId(), assetId)
                .header(HttpHeaders.AUTHORIZATION, bearer(ADMIN)))
        .andExpect(status().isOk());
    return assetId;
  }

  /** A ready video, inside a published module assigned to the learner's team. */
  private UUID readyAssetInAssignedModule() throws Exception {
    UUID assetId = uploadedAsset();
    transcoder.finish("job-0", 60);
    poller.poll();

    teamMembers.saveAndFlush(TeamMember.of(ward.getId(), learner.getId(), TeamRole.TEAM_MEMBER));

    JsonNode module =
        JSON.readTree(
            mockMvc
                .perform(
                    post("/api/v1/orgs/{orgId}/modules", clinic.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"With video\"}"))
                .andReturn()
                .getResponse()
                .getContentAsString());
    UUID moduleId = UUID.fromString(module.get("moduleId").asText());

    mockMvc
        .perform(
            put("/api/v1/orgs/{orgId}/modules/{moduleId}/draft/sections", clinic.getId(), moduleId)
                .header(HttpHeaders.AUTHORIZATION, bearer(ADMIN))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sections\":[{\"title\":\"Watch\",\"body\":\"\",\"mediaAssetId\":\""
                    + assetId + "\"}]}"))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post("/api/v1/orgs/{orgId}/modules/{moduleId}/draft/publish", clinic.getId(), moduleId)
                .header(HttpHeaders.AUTHORIZATION, bearer(ADMIN))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"supersedesCompletions\":false}"))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            put("/api/v1/orgs/{orgId}/modules/{moduleId}/teams", clinic.getId(), moduleId)
                .header(HttpHeaders.AUTHORIZATION, bearer(ADMIN))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"teamIds\":[\"" + ward.getId() + "\"]}"))
        .andExpect(status().isOk());

    return assetId;
  }

  private String bearer(String sub) {
    return tokens.bearerFor(sub, sub + "@example.org");
  }

  private AppUser user(String sub) {
    return users
        .findByCognitoSub(sub)
        .orElseGet(
            () ->
                users.saveAndFlush(
                    AppUser.builder()
                        .email(sub + "@example.org")
                        .cognitoSub(sub)
                        .fullName(sub)
                        .profileCompleted(true)
                        .build()));
  }
}
