package io.simplicity.training.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.simplicity.training.model.enums.OrgRole;
import io.simplicity.training.model.enums.PlatformRole;
import io.simplicity.training.model.enums.TeamRole;
import io.simplicity.training.model.enums.UserStatus;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * The cached principal has to survive a round trip through Redis. Getting this wrong does not fail
 * loudly — reads just miss and every request quietly goes to the database instead — so it is worth
 * asserting directly rather than only through the cache's behaviour.
 */
class AppPrincipalSerialisationTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void survivesARoundTripWithEveryFieldIntact() throws Exception {
    UUID orgId = UUID.randomUUID();
    UUID teamId = UUID.randomUUID();
    AppPrincipal original =
        new AppPrincipal(
            UUID.randomUUID(),
            "sub-123",
            "clinician@example.org",
            true,
            UserStatus.ACTIVE,
            PlatformRole.SUPER_ADMIN,
            Map.of(orgId, OrgRole.ORG_ADMIN),
            Map.of(teamId, TeamRole.TEAM_MEMBER));

    AppPrincipal restored =
        mapper.readValue(mapper.writeValueAsString(original), AppPrincipal.class);

    assertThat(restored).isEqualTo(original);
    assertThat(restored.orgRoles()).containsEntry(orgId, OrgRole.ORG_ADMIN);
    assertThat(restored.teamRoles()).containsEntry(teamId, TeamRole.TEAM_MEMBER);
    assertThat(restored.isSuperAdmin()).isTrue();
  }

  @Test
  void doesNotWriteDerivedAccessorsTheConstructorCannotAccept() throws Exception {
    AppPrincipal principal =
        new AppPrincipal(
            UUID.randomUUID(),
            "sub-456",
            "other@example.org",
            false,
            UserStatus.ACTIVE,
            PlatformRole.STANDARD,
            Map.of(),
            Map.of());

    String json = mapper.writeValueAsString(principal);

    assertThat(json).doesNotContain("superAdmin").doesNotContain("\"active\"");
  }
}
