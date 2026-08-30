# Hardening 1 — Tokens and rate limits — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Removing somebody's access ends it in seconds rather than minutes, a token that is not an access token from a known client is refused, and the rate limits the specs describe actually exist.

**Architecture:** All backend and configuration, plus one iOS string and one client retry. No new components — the revocation machinery already exists and is simply not wired up.

**Tech Stack:** Java 17 / Spring Boot, Redis via `StringRedisTemplate`, Testcontainers, CloudFormation.

**Spec:** [docs/superpowers/specs/2026-08-30-hardening-design.md](../specs/2026-08-30-hardening-design.md)

## Global Constraints

- Java is formatted by Spotless: `./gradlew :spotlessApply` before committing.
- Migrations are additive; this plan needs none.
- Every behaviour change gets a test that fails first. Several of these assert *absence* of a lockout, which is exactly the kind of thing that passes vacuously if written after the fix.
- `./gradlew test` must pass before each commit. Testcontainers provides Postgres and Valkey.
- No secret, client id or pool id moves out of configuration into source.

## The two traps in this work

**Validating `client_id` against one value takes the iOS app offline.** `app.cognito.client-id` is
populated from `${AuthStackName}-WebClientId` in `infra/app.yaml`. iOS authenticates with
`IosClientId`, Android with `AndroidClientId`. The property becomes a list and the template passes
all three; Task 3 has a test asserting each is accepted, which is the thing that would catch a
regression here.

**Revoking as a boolean locks people out.** `isRevoked` currently rejects any token from a subject
for fifteen minutes, including one issued after the revocation. Wired to `removeMember` unchanged,
a clinician removed from one of their two clinics loses access to both and cannot sign back in.
Task 1 changes the meaning before Task 2 creates any callers.

**Order matters:** Task 1 before Task 2, and Task 3's list before anything that validates a client.

---

### Task 1: Revocation means "issued before now", not "banned"

**Files:**
- Modify: `backend/src/main/java/io/simplicity/training/security/TokenRevocationService.java`
- Modify: `backend/src/main/java/io/simplicity/training/security/PrincipalResolutionFilter.java`
- Test: `backend/src/test/java/io/simplicity/training/security/TokenRevocationTest.java` (create)

**Interfaces:**
- Consumes: `StringRedisTemplate`, `AppProperties.auth().accessTokenTtl()`.
- Produces:
  - `TokenRevocationService.revoke(String cognitoSub)` — unchanged signature, now stores an instant
  - `TokenRevocationService.revokedAt(String cognitoSub) -> Instant?`
  - `TokenRevocationService.isRevoked(String cognitoSub, Instant issuedAt) -> Bool` — replaces the
    single-argument form
  - `restore` unchanged

- [ ] **Step 1: Write the failing tests**

```java
@Test
void rejectsATokenIssuedBeforeTheRevocation() {
  revocations.revoke(SUBJECT);

  assertThat(revocations.isRevoked(SUBJECT, Instant.now().minusSeconds(60))).isTrue();
}

@Test
void acceptsATokenIssuedAfterTheRevocation() {
  // The whole point. Without this, removing somebody from one of their two organisations locks
  // them out of both, and out of signing back in, until the entry expires.
  revocations.revoke(SUBJECT);

  assertThat(revocations.isRevoked(SUBJECT, Instant.now().plusSeconds(1))).isFalse();
}

@Test
void aSubjectThatWasNeverRevokedIsNotRevoked() {
  assertThat(revocations.isRevoked(SUBJECT, Instant.now())).isFalse();
}

@Test
void restoreClearsTheRevocation() {
  revocations.revoke(SUBJECT);
  revocations.restore(SUBJECT);

  assertThat(revocations.isRevoked(SUBJECT, Instant.now().minusSeconds(60))).isFalse();
}

@Test
void aTokenWithNoIssuedAtIsTreatedAsOlderThanAnyRevocation() {
  // Fail closed: a token we cannot date must not be given the benefit of the doubt.
  revocations.revoke(SUBJECT);

  assertThat(revocations.isRevoked(SUBJECT, null)).isTrue();
}
```

- [ ] **Step 2: Run to verify they fail**

```bash
cd backend && ./gradlew test --tests "*TokenRevocationTest"
```

Expected: does not compile — `isRevoked` takes one argument.

- [ ] **Step 3: Store the instant**

```java
/** Epoch seconds, so the value is legible in redis-cli and needs no serialisation. */
public void revoke(String cognitoSub) {
  if (cognitoSub == null) {
    return;
  }
  redis
      .opsForValue()
      .set(
          key(cognitoSub),
          Long.toString(Instant.now().getEpochSecond()),
          properties.auth().accessTokenTtl());
}

/**
 * True when this token predates the revocation.
 *
 * <p>An instant rather than a flag, because a flag would reject a token issued *after* the
 * revocation too — locking somebody out of the organisations they still belong to, and out of
 * signing back in, for as long as the entry survives.
 */
public boolean isRevoked(String cognitoSub, Instant issuedAt) {
  Instant revokedAt = revokedAt(cognitoSub);
  if (revokedAt == null) {
    return false;
  }
  // A token we cannot date fails closed.
  return issuedAt == null || issuedAt.isBefore(revokedAt);
}
```

- [ ] **Step 4: Pass the token's `iat` at the call site**

In `PrincipalResolutionFilter`, `jwt.getIssuedAt()` is already available on the decoded token:

```java
if (revocations.isRevoked(cognitoSub, jwt.getIssuedAt())) {
```

Update the comment beside it, which currently explains only half of what the check now does.

- [ ] **Step 5: Run to verify they pass**

```bash
cd backend && ./gradlew test --tests "*TokenRevocationTest" --tests "*PrincipalCacheTest"
```

`PrincipalCacheTest` already exercises `accessRevoked`; both must pass.

- [ ] **Step 6: Commit**

```bash
cd backend && ./gradlew :spotlessApply
git add backend
git commit -m "Revocation voids tokens issued before it, rather than banning a subject"
```

---

### Task 2: Wire revocation to the events that withdraw access

**Files:**
- Modify: `backend/src/main/java/io/simplicity/training/service/OrganisationService.java` (lines ~156, ~203, ~213)
- Test: `backend/src/test/java/io/simplicity/training/OrganisationLifecycleTest.java`

**Interfaces:**
- Consumes: `SessionService.accessRevoked(UUID)`, already written and previously called only from tests.
- Produces: no new API.

- [ ] **Step 1: Write the failing tests**

Cover, as integration tests with a real token:

- A removed member's existing token is refused on the very next request, with 401.
- A member who leaves has their token refused.
- Archiving an organisation refuses its members' existing tokens.
- **A member removed from one organisation, who belongs to another, can still use the product after
  obtaining a fresh token.** Simulated by asserting `isRevoked` is false for an `iat` after the
  removal, since the suite cannot mint a real Cognito token.
- Changing somebody's role does **not** revoke — they keep working, which is the deliberate
  asymmetry in the spec.

- [ ] **Step 2: Run to verify they fail**

```bash
cd backend && ./gradlew test --tests "*OrganisationLifecycleTest"
```

- [ ] **Step 3: Swap the calls**

`removeMember`, `leave` and the per-member loop in `archive` call `sessions.accessRevoked(...)`.
`changeRole` keeps `rolesChanged`. Each gets a one-line comment saying which it is and why, because
the two are a single character apart at the call site and the difference is the whole point.

- [ ] **Step 4: Run to verify they pass**

- [ ] **Step 5: Commit**

```bash
cd backend && ./gradlew :spotlessApply
git add backend
git commit -m "Revoke access when it is withdrawn, not only evict the cache"
```

---

### Task 3: Validate the token properly

**Files:**
- Modify: `backend/src/main/java/io/simplicity/training/config/AppProperties.java`
- Modify: `backend/src/main/java/io/simplicity/training/security/SecurityConfig.java`
- Modify: `backend/src/main/resources/application.yml`
- Modify: `infra/app.yaml`
- Test: `backend/src/test/java/io/simplicity/training/security/JwtValidationTest.java` (create)

**Interfaces:**
- Produces:
  - `AppProperties.Cognito.clientIds() -> List<String>` replacing `clientId()`
  - A `JwtDecoder` with validators for timestamp, issuer, `token_use == "access"`, and `client_id`
    within the configured list

- [ ] **Step 1: Write the failing tests**

Using `NimbusJwtDecoder` against a local key pair, or by unit-testing the `OAuth2TokenValidator`
directly — the latter is simpler and tests the thing that actually changed:

```java
@Test
void acceptsAnAccessTokenFromEachKnownClient() {
  // The test that stops a well-meaning fix taking the iOS app offline. app.cognito.client-id was
  // populated from the *web* client only; validating against it alone rejects every iOS request.
  for (String clientId : List.of(WEB_CLIENT, IOS_CLIENT, ANDROID_CLIENT)) {
    assertThat(validator.validate(accessToken(clientId)).hasErrors()).isFalse();
  }
}

@Test
void refusesAnIdToken() {
  // Cognito signs ID tokens with the same keys, so without this an ID token authorises requests.
  assertThat(validator.validate(idToken(WEB_CLIENT)).hasErrors()).isTrue();
}

@Test
void refusesATokenWithNoTokenUseClaim() { ... }

@Test
void refusesAnUnknownClient() { ... }

@Test
void refusesAnotherIssuer() { ... }
```

- [ ] **Step 2: Run to verify they fail**

- [ ] **Step 3: Make the property a list**

```java
public record Cognito(
    String issuerUri,
    String userPoolId,
    List<String> clientIds,
    @DefaultValue("ap-southeast-2") String region) {
```

Every reference to `clientId()` must be found and updated —
`rg -n "cognito\(\)\.clientId" backend/src` — rather than left to fail at runtime.

- [ ] **Step 4: Build the validator**

```java
return NimbusJwtDecoder.withJwkSetUri(jwkSetUri)
    .build()
    .setJwtValidator(
        new DelegatingOAuth2TokenValidator<>(
            new JwtTimestampValidator(),
            new JwtIssuerValidator(properties.cognito().issuerUri()),
            // Cognito signs ID and access tokens with the same keys, so the only thing separating
            // them is this claim.
            new JwtClaimValidator<String>("token_use", "access"::equals),
            new JwtClaimValidator<List<String>>(
                "client_id", id -> properties.cognito().clientIds().contains(id))));
```

`setJwtValidator` on the built decoder, not `withJwkSetUri(...).jwtProcessorCustomizer(...)`;
the former is the supported seam.

- [ ] **Step 5: Pass all three client ids from the template**

In `infra/app.yaml`, replace the single `COGNITO_CLIENT_ID` with a comma-separated
`COGNITO_CLIENT_IDS` built from the three exports `auth.yaml` already publishes:

```yaml
- Name: COGNITO_CLIENT_IDS
  Value: !Join
    - ','
    - - Fn::ImportValue: !Sub '${AuthStackName}-WebClientId'
      - Fn::ImportValue: !Sub '${AuthStackName}-IosClientId'
      - Fn::ImportValue: !Sub '${AuthStackName}-AndroidClientId'
```

Spring binds a comma-separated environment variable to a `List<String>` without help.

- [ ] **Step 6: Run to verify they pass, then the whole suite**

```bash
cd backend && ./gradlew test
```

The whole suite matters here: every existing integration test mints a token, and a validator that
is too strict will fail them all at once.

- [ ] **Step 7: Commit**

```bash
cd backend && ./gradlew :spotlessApply
git add backend infra
git commit -m "Validate the issuer, the token type and the client"
```

---

### Task 4: Rate limits that exist, and fail the right way

**Files:**
- Modify: `backend/src/main/java/io/simplicity/training/service/RateLimiter.java`
- Modify: `backend/src/main/java/io/simplicity/training/service/InvitationService.java`
- Modify: `backend/src/main/java/io/simplicity/training/service/media/MediaService.java`
- Modify: `backend/src/main/java/io/simplicity/training/service/ReflectionService.java`
- Modify: `backend/src/main/java/io/simplicity/training/config/AppProperties.java`
- Test: `backend/src/test/java/io/simplicity/training/RateLimitTest.java` (create)

**Interfaces:**
- Produces:
  - `RateLimiter.OnOutage { ALLOW, REFUSE }`
  - `tryAcquire(String scope, String key, int limit, Duration window, OnOutage onOutage)`
  - Media: one registration per user per minute, matching the Phase 5 spec
  - Reflections: sixty writes per hour per user

- [ ] **Step 1: Write the failing tests**

- The assistant's thirty-first question in an hour is refused.
- A second upload registration within the minute is refused; one a minute later is allowed.
- The media limit is per user, not global — a second user is unaffected.
- With Redis unavailable, the **assistant** is allowed through and **invitations** are refused. Two
  separate tests, because they are the two halves of the decision and a single parameterised test
  would let one silently follow the other.

Simulate the outage with a `StringRedisTemplate` stub that throws `DataAccessException`.

- [ ] **Step 2: Run to verify they fail**

- [ ] **Step 3: Give the limiter an explicit posture**

```java
public enum OnOutage {
  /** The feature is worth more than the limit. */
  ALLOW,
  /** The limit is worth more than the feature. */
  REFUSE
}
```

`catch (DataAccessException e)` returns `onOutage == OnOutage.ALLOW`, logging either way. No
default: every call site states which it wants, because the right answer differs per feature and a
default would make the wrong one invisible.

- [ ] **Step 4: Apply it**

Assistant `ALLOW` — Redis being down should not stop somebody asking about their training.
Invitations `REFUSE` — the failure mode is the product used as a mail relay.
Media `REFUSE` — the failure mode is unbounded S3 spend.
Reflections `ALLOW` — refusing to record a clinician's reflection because a cache is down is the
worst trade in the product.

- [ ] **Step 5: Run to verify they pass**

- [ ] **Step 6: Commit**

```bash
cd backend && ./gradlew :spotlessApply
git add backend
git commit -m "Rate limits that exist, and that fail the right way per feature"
```

---

### Task 5: The clients recover from a revoked token

**Files:**
- Modify: `ios/Packages/SimplicityApi/Sources/SimplicityApi/BearerInterceptor.swift`
- Modify: `web/src/app/core/auth/auth.service.ts` and its API interceptor
- Test: `ios/Packages/SimplicityApi/Tests/SimplicityApiTests/BearerInterceptorTests.swift`

**Interfaces:**
- Produces: one retry on 401 with a forced token refresh, on both clients.

`BearerInterceptor.retry` currently returns `.dontRetry` always, with a comment asserting that a
401 which survives a refresh means the session is over. That reasoning was right when nothing
revoked tokens; now that revocation exists, the first 401 is exactly the case a refresh fixes.

- [ ] **Step 1: Write the failing test**

- A 401 retries once, having asked for a fresh token.
- A second 401 does not retry again — one retry, not a loop.
- A 403 does not retry, because that is authorisation rather than authentication and a new token
  will not change it.

- [ ] **Step 2 to 5:** fail, implement, pass, commit.

---

### Task 6: The iOS leave warning tells the truth

**Files:**
- Modify: `ios/Packages/SimplicityAdmin/Sources/SimplicityAdmin/Settings/SettingsViewModel.swift`
- Modify: `ios/Packages/SimplicityAdmin/Sources/SimplicityAdmin/Resources/en.lproj/Localizable.strings`
- Modify: `ios/Packages/SimplicityAdmin/Tests/SimplicityAdminTests/SettingsViewModelTests.swift`

The app tells a sole administrator that leaving "would strand this organisation" and to promote
somebody first. It archives the organisation. The comment in the view model calls this "a server
gap"; it is a deliberate decision, and the code says so.

- [ ] **Step 1: Correct the confirmation copy**

The confirmation shown *before* leaving, when the person is the only administrator, says the
organisation will be archived and that its training will no longer be reachable. That is the
warning that matters — after the fact is too late.

Knowing whether they are the only administrator needs the member list, which an administrator may
read. Load it when the Settings screen belongs to an administrator, and fall back to the ordinary
warning when it cannot be loaded.

- [ ] **Step 2: Delete the unreachable branch and its string**

The 409 handling and `settings_leave_last_admin` go, along with the comment claiming a server gap.

- [ ] **Step 3 to 5:** tests, run, commit.

---

### Task 7: Verify against production

**Files:**
- Create: `scripts/verify_hardening.py`

- [ ] **Step 1: Write the check**

1. Two accounts, one organisation, both members, one an administrator.
2. The member calls `/api/v1/me` successfully and the token is kept.
3. The administrator removes them.
4. **The kept token is now refused** — the assertion this whole plan exists for.
5. A freshly authenticated token for the same person works again, proving they are not locked out.
6. An **ID token** is refused where an access token is expected. `pycognito` exposes both, so this
   is a real test rather than a simulated one.
7. A second upload registration within the minute is refused.

- [ ] **Step 2: Run it**

- [ ] **Step 3: Commit**

---

## Self-review against the spec

| Spec item | Covered by |
| --- | --- |
| §3.1 revocation wired to withdrawal | Tasks 1, 2 |
| §3.1 revocation as an instant, not a ban | Task 1, with the lockout test named |
| §3.1 clients ask for a new token | Task 5 |
| §3.2 `token_use`, issuer, client list | Task 3 |
| §3.2 the single-client-id trap | Task 3, asserted for all three |
| §3.4 fail-open versus fail-closed | Task 4 |
| §3.4 missing media and reflection limits | Task 4 |
| §2 the wrong leave warning | Task 6 |

Deferred to the second hardening plan, deliberately: alarms and SNS, Valkey transit encryption, API
security headers, dependency scanning, `audit_event.ip_address`, and the captions size cap. They
are infrastructure and CI rather than request-path security, and mixing them here would make a
large change larger without making it safer.
