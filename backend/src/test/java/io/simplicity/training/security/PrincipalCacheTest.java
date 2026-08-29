package io.simplicity.training.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.simplicity.training.model.entity.AppUser;
import io.simplicity.training.model.entity.OrgMembership;
import io.simplicity.training.model.entity.Organisation;
import io.simplicity.training.model.enums.OrgRole;
import io.simplicity.training.model.enums.OrganisationType;
import io.simplicity.training.support.AbstractIntegrationTest;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;

class PrincipalCacheTest extends AbstractIntegrationTest {

  private static final String SUB = "cached-sub";

  @Autowired private CachingPrincipalService principalCache;
  @Autowired private SessionService sessions;
  @Autowired private TokenRevocationService revocations;

  private static Supplier<Optional<String>> addressOf(AppUser user) {
    return () -> Optional.of(user.getEmail());
  }

  @Test
  void cachesThePrincipalAfterTheFirstRequest() throws Exception {
    aUser();

    assertThat(redis.hasKey("principal:" + SUB)).isFalse();

    mockMvc
        .perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, tokens.bearerFor(SUB)))
        .andExpect(status().isOk());

    assertThat(redis.hasKey("principal:" + SUB))
        .as("the resolved principal should be cached so later requests skip the joins")
        .isTrue();
  }

  @Test
  void servesTheSameRolesFromCacheAndFromTheDatabase() throws Exception {
    AppUser user = aUser();
    Organisation org = anOrganisation();
    orgMemberships.saveAndFlush(OrgMembership.of(user.getId(), org.getId(), OrgRole.ORG_ADMIN));

    // Populates the cache.
    AppPrincipal fromDatabase = principalCache.resolve(SUB, addressOf(user));
    AppPrincipal fromCache = principalCache.resolve(SUB, addressOf(user));

    assertThat(fromCache).isEqualTo(fromDatabase);
    assertThat(fromCache.orgRoles()).containsEntry(org.getId(), OrgRole.ORG_ADMIN);
  }

  @Test
  void keepsServingStaleRolesUntilSomethingEvicts() {
    AppUser user = aUser();
    Organisation org = anOrganisation();

    principalCache.resolve(SUB, addressOf(user));
    orgMemberships.saveAndFlush(OrgMembership.of(user.getId(), org.getId(), OrgRole.ORG_ADMIN));

    // Doubles as proof that reads genuinely hit Redis: a principal that silently fell through to
    // the database would report the new role here.
    assertThat(principalCache.resolve(SUB, addressOf(user)).orgRoles())
        .as("this is precisely why every mutation must evict rather than rely on the TTL")
        .isEmpty();
  }

  @Test
  void picksUpNewRolesOnceTheMutationEvicts() {
    AppUser user = aUser();
    Organisation org = anOrganisation();
    principalCache.resolve(SUB, addressOf(user));

    orgMemberships.saveAndFlush(OrgMembership.of(user.getId(), org.getId(), OrgRole.ORG_ADMIN));
    sessions.rolesChanged(user.getId());

    assertThat(principalCache.resolve(SUB, addressOf(user)).orgRoles())
        .containsEntry(org.getId(), OrgRole.ORG_ADMIN);
  }

  @Test
  void blocksAStillValidTokenAsSoonAsAccessIsRevoked() throws Exception {
    AppUser user = aUser();
    String bearer = tokens.bearerFor(SUB);

    mockMvc
        .perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, bearer))
        .andExpect(status().isOk());

    sessions.accessRevoked(user.getId());

    mockMvc
        .perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, bearer))
        .andExpect(
            status()
                .isUnauthorized());
    assertThat(revocations.isRevoked(SUB)).isTrue();
  }

  @Test
  void letsAReinstatedUserBackIn() throws Exception {
    AppUser user = aUser();
    sessions.accessRevoked(user.getId());

    sessions.accessRestored(user.getId());

    mockMvc
        .perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, tokens.bearerFor(SUB)))
        .andExpect(status().isOk());
  }

  @Test
  void roundTripsEveryFieldThroughRedis() throws Exception {
    AppUser user = aUser();
    Organisation org = anOrganisation();
    orgMemberships.saveAndFlush(OrgMembership.of(user.getId(), org.getId(), OrgRole.ORG_MEMBER));
    sessions.rolesChanged(user.getId());

    principalCache.resolve(SUB, addressOf(user));
    AppPrincipal cached = principalCache.resolve(SUB, addressOf(user));

    assertThat(cached.userId()).isEqualTo(user.getId());
    assertThat(cached.email()).isEqualTo(user.getEmail());
    assertThat(cached.cognitoSub()).isEqualTo(SUB);
    assertThat(cached.orgRoles()).containsEntry(org.getId(), OrgRole.ORG_MEMBER);

    // Serving from cache must not change what the endpoint reports.
    mockMvc
        .perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, tokens.bearerFor(SUB)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.organisations[0].orgRole").value("ORG_MEMBER"));
  }

  private AppUser aUser() {
    return users.saveAndFlush(
        AppUser.builder().email("cached@example.org").cognitoSub(SUB).build());
  }

  private Organisation anOrganisation() {
    return organisations.saveAndFlush(
        Organisation.builder()
            .name("Cache Org")
            .slug("cache-org")
            .organisationType(OrganisationType.CLINIC)
            .build());
  }
}
