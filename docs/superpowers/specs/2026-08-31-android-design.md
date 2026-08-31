# Android — Design

## 1. Scope

A native Android client with the same reach as the iOS one: a clinician signs in, works through
assigned modules, watches the video, sits the quiz, keeps a private journal, and asks about the
training. An administrator does everything the web allows, including authoring a module and
uploading its video from the camera.

Android was scoped into Phase 5 alongside iOS and never started, because no Android SDK was
installed. **That is no longer true** — `android-commandlinetools` is installed with
`platforms;android-36`, `build-tools;36.0.0` and `platform-tools`, and the Kotlin API client
generates cleanly into `android/api-client`. It is the only commitment in the project that has gone
unmet, which is why it is being built rather than dropped.

### Definition of done

A clinician installs from the Play Console internal testing track, signs in with the account they
already use on the web, opens an assigned module, watches its video **with captions**, passes the
quiz, and sees the same completion the web reports. An administrator films a demonstration, uploads
it into a draft, publishes, and a learner sees it. Everything is exercised by tests that run on this
machine, and the end-to-end path is verified against production the way every prior phase was.

## 2. Decisions and their rationale

### 2.1 Parity with iOS, and the honest cost of it

The iOS spec argued for full parity on the grounds that the camera makes the phone the *best* place
to record a demonstration, and that once the upload path exists the rest of authoring is cheap. That
argument holds identically here.

What is different is that this is now the **third** client. Every API change already has to reach
the web, the generated Swift client and the integration tests; Android makes four. That cost is
real and worth stating rather than discovering: a reduced learner-only Android app would be
meaningfully cheaper, and it would also be the second-class client that an administrator learns not
to trust.

Parity is the right call, but it is a decision to spend maintenance rather than an obvious win.

### 2.2 Jetpack Compose, mirroring the iOS structure rather than inventing one

The iOS app is ten Swift packages with a thin app shell. Android mirrors that as Gradle modules
with the same names and the same boundaries, because the boundaries were chosen for reasons that do
not change with the language — `SimplicityAssistant` never depending on `SimplicityReflect` is a
privacy property, not a Swift one.

| iOS package | Android module | Contents |
| --- | --- | --- |
| SimplicityFoundation | `:foundation` | Preferences, secure storage |
| SimplicityApi | `:api` | Generated client plus the token interceptor |
| SimplicityDesign | `:design` | Theme, shared composables, Markdown rendering |
| SimplicityServices | `:services` | One interface per API area |
| SimplicityAuth | `:auth` | Cognito, password policy, identifier warnings |
| SimplicityLearn | `:learn` | Dashboard, modules, reader, quiz |
| SimplicityReflect | `:reflect` | Journal and editor |
| SimplicityAssistant | `:assistant` | Ask sheet |
| SimplicityAdmin | `:admin` | Members, teams, invitations, authoring, upload |
| SimplicityTesting | `:testing` | Fakes and test infrastructure |

Equivalents, chosen to match what each iOS dependency does rather than by popularity: **Hilt** for
Factory, **MockK** for Mockable, **coroutines and Flow** for async/await, **Retrofit** because the
generator already emits it.

### 2.3 The generated client is already configured, and already works

`api-contract/generator/kotlin.yaml` was committed during Phase 5 in anticipation and never run.
Running it produces 135 files across nine API interfaces — Retrofit2, coroutines,
kotlinx.serialization, package `io.simplicity.training.api`.

It is committed like the Swift client, for the same reason: a consumer that cannot build without
first running a generator is a consumer that breaks on a fresh clone.

### 2.4 Amplify Android, and the app client that already exists

The Cognito user pool already has an Android app client — `7ho33kb0dael152r14uurjk9id` — created by
`infra/auth.yaml` long before anything used it. The hardening work then made the backend validate
`client_id` against **all three** clients, so the server already accepts Android tokens today.

Nothing server-side has to change for this app to sign in. That is unusual enough to be worth
saying plainly.

### 2.5 Captions work here, and that is a real difference from iOS

The iOS app cannot show captions. `AVFoundation` will not side-load a WebVTT track onto a
progressive MP4; it needs HLS, which is a transcode and migration change nobody has funded.

Media3 has no such limitation: `MediaItem.SubtitleConfiguration` attaches a WebVTT track to a
progressive MP4 directly. So **Android ships the accessibility feature iOS is missing**, using
caption files the authoring flow already produces and stores.

This is worth naming for two reasons. It is the first capability where the clients genuinely
diverge, and it means a clinician who needs captions should be told to use Android or the web — a
support answer somebody has to know.

### 2.6 Online only, like iOS

No offline reading, no download, no local cache of module content. The same reasoning: reflections
are the only thing a clinician creates that would hurt to lose, and they are written in one sitting
and posted immediately.

### 2.7 Reflections carry the same promise, and Android adds the same risk

A reflection is private to its author. The identifier warnings that exist on iOS exist here, in the
same words, because a clinician typing a patient's name into a phone is the failure mode both apps
share. Nothing is written to disk before it is posted, and the editor keeps no draft.

Android adds one hazard iOS does not have: **keyboard prediction learning what is typed**.
`InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS` on the reflection editor, so the phone's dictionary does
not quietly retain fragments of a clinical note.

### 2.8 Background upload with WorkManager

The iOS app uses a background `URLSession` so a several-hundred-megabyte upload survives the app
being backgrounded. WorkManager is the equivalent and the same reasoning applies: hospital wifi and
a large file mean the upload will not finish while somebody watches it.

The five-hundred-megabyte cap and the one-per-minute rate limit are enforced server-side — this
time genuinely, since the hardening work added the limiter the Phase 5 spec had wrongly claimed
already existed.

## 3. Testing

Three levels, in descending order of how often they run.

**JVM unit tests** per module, covering view models, services and utilities with no emulator. This
is where nearly all of it lives, and it is fast enough to run on every change.

**Compose UI tests** for the screens whose behaviour is not obvious from the view model — the quiz
in particular, where the lock until every section is read is a property worth asserting against the
real composable.

**Instrumented tests** on an emulator, kept to a smoke path: launch, reach sign-in, sign in, open a
module. Emulators are slow and flaky in CI, so this is deliberately thin.

Production verification follows every prior phase: a script under `scripts/` driving the real API.

## 4. CI

An `android` job on **ubuntu-latest**, which matters more than it sounds. The iOS job runs on
`macos-26` at ten times the billing rate and takes twenty minutes; Android builds on Linux at
standard rate. It can therefore run on every push without the path filtering iOS needs.

The emulator smoke test is the exception and runs only when `android/` changes, because hardware
acceleration in CI is the slowest and least reliable part of Android testing.

## 5. Distribution

**This needs a Google Play Console account, which costs twenty-five US dollars once and does not
exist yet.** It is the Android equivalent of the App Store Connect setup that blocked iOS for a day,
and it should be started early rather than discovered at the end.

Internal testing on Play distributes to up to a hundred testers with no review, which is the
equivalent of TestFlight internal testing. Signing uses Play App Signing with an upload key held
locally, mirroring the iOS arrangement where Apple holds the distribution certificate.

## 6. Out of scope

Wear OS. Tablet-specific layouts — the app runs on a tablet at phone proportions until someone asks
otherwise, which is the same position iOS takes. Widgets. Push notifications, which need FCM, a
device token table and a sending path server-side, and which are out of scope on iOS for the same
reason. Play Store public release beyond internal testing.

## 7. Risks

| Risk | Response |
| --- | --- |
| A third client triples the cost of every API change | Named in §2.1 as a cost being accepted, not avoided |
| The Play Console account blocks distribution at the end | Named in §5; start it before writing code |
| Emulator tests are slow and flaky in CI | Kept to a smoke path, and only on `android/` changes |
| Captions diverging between clients confuses support | Named in §2.5 so the answer exists before the question |
| Amplify Android behaves differently from Amplify Swift | The token path is verified against production, not just mocked |
| Compose and SwiftUI drift apart in wording | The copy is asserted in tests on both, as the iOS work already does |
