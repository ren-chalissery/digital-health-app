package io.simplicity.training.support;

import io.simplicity.training.TestcontainersConfiguration;
import io.simplicity.training.repository.AppUserRepository;
import io.simplicity.training.repository.AuditEventRepository;
import io.simplicity.training.repository.InvitationRepository;
import io.simplicity.training.repository.OrgMembershipRepository;
import io.simplicity.training.repository.OrganisationRepository;
import io.simplicity.training.repository.TeamMemberRepository;
import io.simplicity.training.repository.TeamRepository;
import io.simplicity.training.support.TestJwtConfiguration.TestTokenFactory;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.web.servlet.MockMvc;

/** Full-stack tests: real HTTP handling, real Postgres, real Redis, locally signed tokens. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TestJwtConfiguration.class})
public abstract class AbstractIntegrationTest {

  @Autowired protected MockMvc mockMvc;
  @Autowired protected TestTokenFactory tokens;
  @Autowired protected StringRedisTemplate redis;

  @Autowired protected AppUserRepository users;
  @Autowired protected OrganisationRepository organisations;
  @Autowired protected OrgMembershipRepository orgMemberships;
  @Autowired protected TeamRepository teams;
  @Autowired protected TeamMemberRepository teamMembers;
  @Autowired protected InvitationRepository invitations;
  @Autowired protected AuditEventRepository auditEvents;

  /**
   * The containers are shared across the whole suite, so each test starts from a clean slate
   * rather than inheriting rows from whichever test ran before it.
   */
  @BeforeEach
  void resetState() {
    auditEvents.deleteAllInBatch();
    invitations.deleteAllInBatch();
    teamMembers.deleteAllInBatch();
    teams.deleteAllInBatch();
    orgMemberships.deleteAllInBatch();
    users.deleteAllInBatch();
    organisations.deleteAllInBatch();
    redis.getConnectionFactory().getConnection().serverCommands().flushDb();
  }
}
