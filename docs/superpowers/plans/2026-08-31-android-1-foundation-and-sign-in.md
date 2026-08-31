# Android 1 — Foundation and sign-in — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A Gradle project that builds, and a clinician can sign in with the account they already use on the web and reach a screen that proves the API answered them.

**Architecture:** Multi-module Gradle mirroring the iOS packages, Jetpack Compose, Hilt, the already-generated Retrofit client.

**Tech Stack:** Kotlin 2.4.0, Gradle 8.14.5, Compose, Hilt, Amplify Android, MockK, Retrofit 3.0.0.

**Spec:** [docs/superpowers/specs/2026-08-31-android-design.md](../specs/2026-08-31-android-design.md)

## Global Constraints

- The SDK is at `/opt/homebrew/share/android-commandlinetools`. `ANDROID_HOME` is **not** exported
  in the shell profile, so `local.properties` carries `sdk.dir` and is gitignored.
- `./gradlew test` passes before each commit. No emulator is needed for anything in this slice.
- Nothing in `android/api-client` is hand-edited. It is generated, and CI now fails if it drifts.
- Copy that appears on both clients is asserted in tests on both, so the wording cannot drift
  silently.

## What is already true

Verified before planning, and each removes work this plan would otherwise carry:

- **The Cognito Android app client exists** — `7ho33kb0dael152r14uurjk9id` — and the backend already
  validates against it, because the hardening work made `COGNITO_CLIENT_IDS` hold all three.
  Nothing server-side changes in this slice.
- **The Kotlin client is generated and committed**: 135 files, nine API interfaces.
- **CI checks it for drift** alongside Swift and Angular.

## The traps in this slice

**The generated client is a standalone Gradle project.** `android/api-client/build.gradle` declares
its own `buildscript`, wrapper and `maven-publish`, because the generator assumes it is the whole
build. Included naively as a subproject it fights the root build. Task 1 decides how to consume it
before anything depends on it.

**It is a plain Kotlin JVM module, not an Android library.** That is fine — Retrofit and
kotlinx.serialization need no Android APIs — but it means it cannot use Android types, and anything
Android-specific has to live in `:api` rather than be patched into the generated code.

**Revocation is now a live behaviour, not a theoretical one.** The backend voids tokens issued
before a membership change, and a token minted in the *same second* as the revocation is refused
too. iOS learned this the hard way and answers with a single forced refresh throttled to two
seconds. Android must do the same, and Task 4 covers it — a client without it fails every request
until the token expires.

---

### Task 1: A Gradle project that builds

**Files:**
- Create: `android/settings.gradle.kts`, `android/build.gradle.kts`, `android/gradle/libs.versions.toml`
- Create: `android/gradle.properties`, `android/local.properties` (gitignored)
- Create: `android/app/build.gradle.kts` and a minimal `MainActivity`
- Modify: `android/api-client/build.gradle` — reduce to a subproject

**Interfaces:**
- Produces: `./gradlew :app:assembleDebug` succeeding from a clean checkout.

- [ ] **Step 1: Decide how the generated client is consumed**

Include it as `:api-client` and **replace** its generated `build.gradle` with a subproject one:
`kotlin("jvm")`, `kotlinx-serialization`, Retrofit, and nothing else. Delete the wrapper, the
`buildscript` block, `maven-publish` and Spotless, which exist because the generator assumes it
owns the build.

Add `android/api-client/build.gradle` to the generator's `.openapi-generator-ignore`, or the next
`npm run generate:android` restores the standalone version and the build breaks. **Verify this by
regenerating and building**, not by reading the ignore file.

- [ ] **Step 2: A version catalog, not scattered version strings**

`gradle/libs.versions.toml` holds every version. Kotlin 2.4.0 and Gradle 8.14.5 come from what the
generator already targets, so the client and the app agree.

- [ ] **Step 3: `local.properties` and the SDK location**

```properties
sdk.dir=/opt/homebrew/share/android-commandlinetools
```

Gitignored, and the README says to create it, because the path differs on every machine and CI sets
`ANDROID_HOME` instead.

- [ ] **Step 4: Verify from clean**

```bash
cd android && ./gradlew :app:assembleDebug
```

- [ ] **Step 5: Commit**

---

### Task 2: The module skeleton

**Files:**
- Create: `android/{foundation,api,design,services,auth,testing}/build.gradle.kts` and package roots

Modules mirror the iOS packages by name and boundary. Only the ones this slice needs are created;
`:learn`, `:reflect`, `:assistant` and `:admin` come with their slices.

- [ ] **Step 1: Enforce the boundary that matters**

`:assistant` must never depend on `:reflect`. It has no module yet, so the enforcement is a note in
the root build now and a dependency assertion when `:assistant` exists. The reason is a privacy
property: the assistant reads training content, never a clinician's journal.

- [ ] **Step 2 to 4:** Hilt in `:app`, `@HiltAndroidApp`, verify, commit.

---

### Task 3: `:foundation` — preferences and secure storage

**Files:**
- Create: `android/foundation/src/main/kotlin/.../Preferences.kt`, `SecureStore.kt`
- Test: `android/foundation/src/test/kotlin/.../SecureStoreTest.kt`

**Interfaces:**
- `SecureStore` with `put(key, value)`, `get(key): String?`, `remove(key)`, backed by
  `EncryptedSharedPreferences`.

The iOS equivalent is Keychain-backed with **no disk fallback** — if secure storage is unavailable
the value is not stored at all, rather than quietly written somewhere readable. Android matches
that: a failure to open `EncryptedSharedPreferences` throws rather than falling back to plain
`SharedPreferences`.

- [ ] **Step 1: Write the failing test** — an in-memory fake in `:testing`, and a test asserting a
      stored value round-trips and a removed one is gone.
- [ ] **Step 2 to 5:** fail, implement, pass, commit.

---

### Task 4: `:api` — the token path, including the retry iOS had to learn

**Files:**
- Create: `android/api/src/main/kotlin/.../ApiConfiguration.kt`, `BearerInterceptor.kt`, `ApiAdapter.kt`
- Test: `android/api/src/test/kotlin/.../BearerInterceptorTest.kt`

**Interfaces:**
- `ApiAdapter` with `baseUrl`, `suspend fun accessToken(): String?`, and
  `suspend fun refreshedAccessToken(): String?`
- An OkHttp `Interceptor` attaching the bearer **per request**, and an `Authenticator` handling 401.

- [ ] **Step 1: Write the failing tests**

The same four properties iOS asserts, because they were all learned from real failures:

- The token is resolved per request, not captured once. A token cached at construction goes stale
  fifteen minutes in.
- A 401 triggers **one** retry, having forced a refresh.
- A second 401 does not retry again, so a genuinely dead session ends instead of looping.
- A 403 does not retry. That is authorisation, and a new token says nothing new about it.

- [ ] **Step 2: Implement, and throttle the refresh**

OkHttp's `Authenticator` is the right seam: it is called precisely on 401 and its return value is
the retried request. Bound repeated refreshes to **two seconds**.

Two seconds is not arbitrary. Measured against production: a token minted in the same second as a
revocation is refused, because a JWT `iat` is whole seconds and the server cannot order them, so it
fails closed. A refresh that lands inside that second is rejected too, and only the next one
succeeds. The interval has to clear a second boundary while keeping the user waiting as briefly as
possible.

- [ ] **Step 3 to 5:** pass, verify against the whole suite, commit.

---

### Task 5: `:auth` — Cognito, and the policy the backend enforces

**Files:**
- Create: `android/auth/src/main/kotlin/.../AuthService.kt`, `AmplifyAuthService.kt`,
  `PasswordPolicy.kt`, `Identifiers.kt`
- Create: `android/app/src/main/res/raw/amplifyconfiguration.json`
- Test: `PasswordPolicyTest.kt`, `IdentifiersTest.kt`

**Interfaces:**
- `AuthService`: `signUp`, `signIn`, `confirmEmail`, `resendCode`, `resetPassword`, `signOut`,
  `accessToken`, `refreshedAccessToken`, `isSignedIn`.

- [ ] **Step 1: Configuration, with the client id that already exists**

Pool `ap-southeast-2_91O0ya5nC`, **Android** client `7ho33kb0dael152r14uurjk9id`, region
`ap-southeast-2`. Committed, as on iOS, because these ship inside the binary and are not secrets.

Use the **Android** client id, not the iOS or web one. They are interchangeable enough to work by
accident and would then misattribute every Android session.

- [ ] **Step 2: Password policy, asserted against the backend's rules**

Twelve characters, upper, lower, digit, and no common patterns. Tested against the same cases as
iOS so a password accepted by one client is accepted by the other.

- [ ] **Step 3: Identifier warnings, in the same words**

NHI numbers, Medicare numbers, dates of birth, emails and names. **Warnings, never blocks** — a
clinician who cannot save is a clinician who writes it somewhere worse. The test asserts the exact
copy shared with iOS.

- [ ] **Step 4 to 6:** failing tests, implement, pass, commit.

---

### Task 6: Sign-in, and a screen that proves the API answered

**Files:**
- Create: Compose screens for sign-in, sign-up, confirm-email and forgot-password, with view models
- Create: `android/app/src/main/kotlin/.../AppRouter.kt`
- Test: view-model tests per screen

- [ ] **Step 1: The router is a state machine, not navigation glue**

Loading, signed out, onboarding, signed in — the same four stages as iOS, decided by `/api/v1/me`.

- [ ] **Step 2 to 5:** view models with failing tests first, screens, verify, commit.

- [ ] **Step 6: Prove it against production**

Extend `scripts/` with an Android sign-in check, or reuse `verify_ios_signin.py` — the token path is
identical and the check is client-agnostic. Reuse if it needs no changes; a second near-identical
script is worse than one shared.

---

### Task 7: CI

**Files:**
- Modify: `.github/workflows/ci.yml`

- [ ] **Step 1: An `android` job on `ubuntu-latest`**

Standard billing rather than the ten-times rate the iOS job pays on `macos-26`, so it runs on every
push with no path filter. Set up JDK 17, the Android SDK, cache Gradle, run `./gradlew test`.

- [ ] **Step 2: Do not gate deploy on it**

`deploy` needs `backend`, `clients` and `web`. Android failing must not stop a backend release, for
the same reason iOS does not.

- [ ] **Step 3: Verify it fails when it should** — push a deliberately broken test and confirm the
      job goes red before removing it. A green CI job that cannot fail is worse than none.

---

## Self-review against the spec

| Spec item | Covered by |
| --- | --- |
| §2.2 modules mirroring iOS | Tasks 1, 2 |
| §2.2 `:assistant` never depends on `:reflect` | Task 2, enforced when the module exists |
| §2.3 generated client committed | Task 1, with the regeneration trap |
| §2.4 Amplify and the existing app client | Task 5 |
| §2.7 identifier warnings | Task 5 |
| §4 CI on Linux | Task 7 |

Not in this slice, and each gets its own: learning and media, reflect and assistant, organisation
administration, authoring and upload, Play Console distribution.
