# Android 2 — Learn and media — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A clinician signs in, sees their assigned modules, reads one, watches its video **with captions**, sits the quiz, and sees the same completion the web reports.

**Architecture:** `:services` wrapping the generated client, `:learn` holding the screens, Media3 for playback.

**Spec:** [docs/superpowers/specs/2026-08-31-android-design.md](../specs/2026-08-31-android-design.md)

## Global Constraints

- `./gradlew test` passes before each commit, and the test count is read from the XML rather than
  inferred from a green build.
- Nothing computes progress. Status, completion and marking are the server's answers, which is what
  keeps the app and the web agreeing about the same clinician.
- Copy shared with iOS is asserted in tests on both.

## What this slice is really for

**Captions.** Everything else here is parity work — the same screens iOS already has, in Compose.
Captions are the one thing Android can do that iOS cannot, and the reason is worth restating so it
is not "fixed" later by someone who thinks iOS was merely lazy:

`AVFoundation` will not side-load a WebVTT track onto a progressive MP4. It needs HLS, which means
a transcode change, a data migration and republishing every module. Media3 attaches the track
directly with `MediaItem.SubtitleConfiguration`.

The caption files already exist — the authoring flow produces them and `PlaybackResponse` already
carries `captionUrl`. Nothing server-side changes.

---

### Task 1: `:services` — the API, wrapped

**Files:**
- Create: `android/services/src/main/kotlin/.../SessionService.kt`, `LearningService.kt` and
  implementations
- Create: `android/services/src/main/kotlin/.../ServicesModule.kt` (Hilt)
- Test: service tests against MockWebServer

**Interfaces:** mirror `LearningService.swift` method for method — `assignedModules`, `module`,
`quiz`, `completeSection`, `submitAttempt`, `playback`.

- [ ] **Step 1: Decide what a service adds over the generated client**

Only two things: it returns domain results rather than Retrofit `Response`, and it turns a non-2xx
into a typed failure. Anything more belongs in a view model.

- [ ] **Step 2: `SessionService`, and the thing sign-in is waiting for**

`refresh()` reads `/api/v1/me` and answers the question slice 1 stubbed as `false`: whether the
profile is complete. Wire it into `MainActivity` so `onSignedIn` stops lying.

- [ ] **Step 3 to 5:** failing tests against MockWebServer, implement, pass, commit.

---

### Task 2: `:design` — Markdown that renders module text

**Files:**
- Create: `android/design/src/main/kotlin/.../Markdown.kt`
- Test: `MarkdownTest.kt`

Module sections are Markdown. iOS has a small parser handling headings, paragraphs, lists and code
blocks, and deliberately **ignores images and strips unsafe schemes** — `javascript:` and `tel:`
links in particular.

- [ ] **Step 1: Port the same rules, and assert the same refusals**

The security-relevant cases are the ones to write first: a `javascript:` link must not survive, and
an image must not render. A module is authored by an administrator, so this is not defence against
a stranger — but an administrator's account can be compromised, and the phone should not be the
weakest reader of the same content.

- [ ] **Step 2 to 4:** implement, pass, commit.

---

### Task 3: Module list and dashboard

**Files:**
- Create: `android/learn/**` — module, view models and screens
- Modify: `settings.gradle.kts` to include `:learn`

- [ ] **Step 1: Status wording matches the web exactly**

Not approximately. A clinician comparing the phone to the web should see the same words for the
same state, and the test asserts them.

- [ ] **Step 2 to 5:** failing view-model tests, screens, verify, commit.

---

### Task 4: The reader, and the quiz that stays locked

**Files:**
- Create: `ModuleReaderViewModel.kt`, `ModuleReaderScreen.kt`, `QuizViewModel.kt`, `QuizScreen.kt`

- [ ] **Step 1: The lock is the property worth testing**

The quiz is unavailable until every section is read. That is the server's rule, and the client
must not present it as available and then fail — so the test asserts the button is disabled with
one section outstanding, and enabled with none.

- [ ] **Step 2: Feedback per question, and completion from the server**

The attempt result says which answers were wrong and why. Completion is what the server reports,
never inferred from a local score.

- [ ] **Step 3 to 5:** failing tests, implement, pass, commit.

---

### Task 5: Video, with captions

**Files:**
- Create: `android/learn/src/main/kotlin/.../VideoSection.kt`
- Modify: `gradle/libs.versions.toml` for Media3

- [ ] **Step 1: Attach the caption track**

```kotlin
MediaItem.Builder()
    .setUri(playback.url)
    .setSubtitleConfigurations(
        captionUrl?.let {
            listOf(
                MediaItem.SubtitleConfiguration.Builder(it.toUri())
                    .setMimeType(MimeTypes.TEXT_VTT)
                    .setLanguage("en")
                    .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                    .build(),
            )
        } ?: emptyList(),
    )
    .build()
```

`SELECTION_FLAG_DEFAULT` so captions are **on by default**. For a training product used in shared
clinical spaces, defaulting to on is the accessible choice and costs nothing to turn off.

- [ ] **Step 2: Refuse a URL that is not HTTPS**

iOS does this and so should Android. A playback URL arrives from the API and should always be
presigned HTTPS; anything else is a bug or worse, and playing it would be the wrong response.

- [ ] **Step 3 to 5:** tests, implement, pass, commit.

- [ ] **Step 6: Record the divergence where support will find it**

The README's known-gaps table says captions do not work on iOS. Once this lands it must say
captions work on **Android and web, not iOS**, because that is the answer somebody will need.

---

### Task 6: Verify against production

- [ ] Reuse `scripts/verify_ios_learn.py` if it needs no changes — it drives the API, not a client,
      and a second near-identical script is worse than one shared. Rename it if the name is now
      misleading.
- [ ] Confirm a real caption file is served for a real module, since that is the one behaviour with
      no iOS precedent to inherit.
