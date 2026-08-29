package io.simplicity.training.support;

import io.simplicity.training.TestcontainersConfiguration;
import io.simplicity.training.repository.AppUserRepository;
import io.simplicity.training.repository.AuditEventRepository;
import io.simplicity.training.repository.InvitationRepository;
import io.simplicity.training.repository.ModuleRepository;
import io.simplicity.training.repository.ModuleSectionRepository;
import io.simplicity.training.repository.ModuleVersionRepository;
import io.simplicity.training.repository.MediaAssetRepository;
import io.simplicity.training.repository.OrgMembershipRepository;
import io.simplicity.training.repository.QuizAttemptRepository;
import io.simplicity.training.repository.QuizOptionRepository;
import io.simplicity.training.repository.QuizQuestionRepository;
import io.simplicity.training.repository.OrganisationRepository;
import io.simplicity.training.repository.TeamMemberRepository;
import io.simplicity.training.repository.TeamModuleAssignmentRepository;
import io.simplicity.training.repository.TeamRepository;
import io.simplicity.training.repository.UserModuleCompletionRepository;
import io.simplicity.training.repository.UserSectionProgressRepository;
import io.simplicity.training.support.TestJwtConfiguration.FakeCognitoUserDirectory;
import io.simplicity.training.support.TestJwtConfiguration.TestTokenFactory;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** Full-stack tests: real HTTP handling, real Postgres, real Redis, locally signed tokens. */
@SpringBootTest
@AutoConfigureMockMvc
// Bucket names only. The object store and transcoder behind them are fakes, so nothing here
// reaches AWS; without them the media service correctly refuses to work at all.
@TestPropertySource(
    properties = {
      "app.media.upload-bucket=test-upload",
      "app.media.asset-bucket=test-media",
      "app.media.transcode-queue-arn=arn:aws:mediaconvert:test:0:queues/test",
      "app.media.transcode-role-arn=arn:aws:iam::0:role/test"
    })
@Import({
  TestcontainersConfiguration.class,
  TestJwtConfiguration.class,
  TestMediaConfiguration.class
})
public abstract class AbstractIntegrationTest {

  @Autowired protected MockMvc mockMvc;
  @Autowired protected TestTokenFactory tokens;
  @Autowired protected StringRedisTemplate redis;
  @Autowired protected FakeCognitoUserDirectory cognitoDirectory;

  @Autowired protected AppUserRepository users;
  @Autowired protected OrganisationRepository organisations;
  @Autowired protected OrgMembershipRepository orgMemberships;
  @Autowired protected TeamRepository teams;
  @Autowired protected TeamMemberRepository teamMembers;
  @Autowired protected InvitationRepository invitations;
  @Autowired protected AuditEventRepository auditEvents;
  @Autowired protected ModuleRepository modules;
  @Autowired protected ModuleVersionRepository moduleVersions;
  @Autowired protected ModuleSectionRepository moduleSections;
  @Autowired protected TeamModuleAssignmentRepository moduleAssignments;
  @Autowired protected UserSectionProgressRepository sectionProgress;
  @Autowired protected UserModuleCompletionRepository moduleCompletions;
  @Autowired protected QuizQuestionRepository quizQuestions;
  @Autowired protected QuizOptionRepository quizOptions;
  @Autowired protected QuizAttemptRepository quizAttempts;
  @Autowired protected MediaAssetRepository mediaAssets;
  @Autowired protected TestMediaConfiguration.RecordingObjectStore objectStore;
  @Autowired protected TestMediaConfiguration.ScriptedTranscoder transcoder;

  /**
   * The containers are shared across the whole suite, so each test starts from a clean slate
   * rather than inheriting rows from whichever test ran before it.
   */
  @BeforeEach
  void resetState() {
    // Deepest dependants first: progress and completions reference sections and versions, which
    // reference the module, which references the organisation.
    quizAttempts.deleteAllInBatch();
    quizOptions.deleteAllInBatch();
    quizQuestions.deleteAllInBatch();
    moduleCompletions.deleteAllInBatch();
    sectionProgress.deleteAllInBatch();
    moduleAssignments.deleteAllInBatch();
    moduleSections.deleteAllInBatch();
    moduleVersions.deleteAllInBatch();
    modules.deleteAllInBatch();
    mediaAssets.deleteAllInBatch();
    auditEvents.deleteAllInBatch();
    invitations.deleteAllInBatch();
    teamMembers.deleteAllInBatch();
    teams.deleteAllInBatch();
    orgMemberships.deleteAllInBatch();
    users.deleteAllInBatch();
    organisations.deleteAllInBatch();
    redis.getConnectionFactory().getConnection().serverCommands().flushDb();
    cognitoDirectory.reset();
    objectStore.reset();
    transcoder.reset();
  }
}
