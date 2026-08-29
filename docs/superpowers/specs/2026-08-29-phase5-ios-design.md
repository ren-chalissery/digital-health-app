# Phase 5 — iOS — Design

Date: 2026-08-29
Status: approved, not yet implemented
Follows: [Phase 1](2026-08-29-digital-health-app-phase1-design.md), [Phase 2a](2026-08-29-phase2a-modules-and-learning-design.md), [Phase 4](2026-08-29-phase4-assistant-design.md)

## 1. Scope

A native iOS app with the same reach as the web application: a clinician signs in, works through
assigned modules, watches the video, sits the quiz, keeps a private journal, and asks about the
training. An administrator does everything the web lets them do, including authoring a module and
uploading its video.

Phase 5 as originally written also covered Android and hardening. Those are separate work with
separate specs. Android is not attempted here because no Android SDK is installed on the build
machine, and a client nobody can compile is worse than one nobody has started.

### Definition of done

A clinician installs the app from TestFlight, signs in with their existing account, opens an
assigned module, watches its video with captions, passes the quiz, and sees the same completion the
web reports. An administrator films a demonstration on the phone, uploads it into a draft module,
publishes, and a learner sees it. Everything is exercised by tests that run on this machine, and the
end-to-end path is verified against production the way every prior phase was.

## 2. Decisions and their rationale

### 2.1 Full parity, because authoring on a phone is not obviously worse

The instinct is to build a learner-only app and leave authoring to the desktop, on the grounds that
nobody writes a training module on a phone. That is right about markdown and wrong about video.

Recording a demonstration and uploading it is the one task the phone does *better*: the camera is
already there, and the alternative is filming on a phone, transferring to a laptop, and uploading
from there. Since the upload path has to exist anyway, the rest of authoring costs comparatively
little, and a half-featured admin experience is its own kind of friction — an administrator who
cannot fix a typo without finding a laptop will not trust the app for anything.

### 2.2 It follows `tinderbox2_ionic`, but that repository's direction rather than its history

The conventions come from `tinderbox2_ionic`: Factory for dependency injection with `Container+`
extensions, MVVM with per-area routers, folders organised by feature rather than by type, services
as a `@Mockable` protocol plus an `Impl`, a dedicated testing package, `.xcstrings` localisation,
and a thin app target whose job is wiring.

Where that repository's own documentation marks something as legacy, this app starts where it is
heading rather than where it has been. So: async/await instead of RxSwift, which its docs call the
legacy reactive layer; `@Observable` instead of `ObservableObject`, available because there is no
iOS 16 to support; Swift Testing and Mockable from the first commit, skipping the XCTest and
Mockingbird migration it is still finishing. Copying the destination costs nothing now and saves the
same migration later.

Two things are deliberately not copied. Realm, because the app is online-only. Duplicate targets for
white-labelling, because there is one product.

### 2.3 Ten packages, not thirty-three

`tinderbox2_ionic` has thirty-three, and that decomposition is the product of years of pressure
rather than a starting position. Ten covers the same boundaries at this size: five foundational
(`Foundation`, `Testing`, `Api`, `Design`, `Services`) and five by feature (`Auth`, `Learn`,
`Reflect`, `Assistant`, `Admin`).

The reason to have packages at all is not tidiness. Logic inside a package is testable with `swift
test` in seconds and no simulator, so the fast tests stay fast and only genuine UI tests pay for a
simulator boot. Package boundaries are also enforced by the compiler, which is the same discipline
the backend gets from its module split, and the reason `Admin` cannot quietly reach into `Learn`.

### 2.4 The Xcode project is committed, and the thin shell is what makes that bearable

An `.xcodeproj` is a large plist that merges badly and corrupts under programmatic editing, which
argues for generating it from a manifest with XcodeGen. `tinderbox2_ionic` commits it instead, and
at this structure that is the better choice: because the app target holds almost nothing, adding a
source file touches no project file at all — it goes into a package. The project changes only when a
whole package is added or a build setting moves, which is rare enough to review by hand.

Choosing XcodeGen would mean a build-time dependency and a second description of the project for
every contributor to learn, to solve a problem the thin shell already removes.

### 2.5 The API client is generated from the same document as the web one

`api-contract/generator/swift.yaml` has been committed since Phase 1, unused because there was no
project to generate into. It runs now, producing an async/await SPM client from the same
`openapi.yaml` the Angular client comes from, so the drift check in `OpenApiSpecTest` protects all
three clients at once.

Its `outputDir` currently reads `../ios/MoxionApiClient` while its `projectName` reads
`SimplicityApi` — a name that leaked in from the work repositories. It becomes
`../ios/Packages/SimplicityApi`.

Generated code is committed, as the web client is, so building the app needs neither Java nor the
generator. It is excluded from SwiftLint, as `tinderbox2_ionic` excludes its own.

### 2.6 Amplify for Cognito, and its own app client

Cognito's SRP exchange is a password-authenticated key agreement, and hand-rolling it means
hand-rolling the crypto. Amplify Swift implements it, matches the Amplify JS flow the web already
uses, and handles refresh. Its Keychain storage is the right place for the tokens.

Phase 1 already provisioned `digital-health-ios-${EnvironmentName}` (`2sihmrojcivd93m8of9q4uk1k6`),
a public client with SRP and refresh flows, a fifteen-minute access token and a ninety-day refresh
token against the web's thirty. Ninety days is deliberate: a clinician opening the app on a ward
should not be made to sign in because they last used it a month ago, and the short access token plus
Cognito's revocation is what limits a lost phone, not the refresh window.

Unlike the web, configuration is baked in at build time through `Config-Shared.xcconfig` rather than
fetched from `config.json`. A web bundle is served per environment and can be told where it lives; an
app binary is submitted to Apple, so its environment is fixed the moment it is built.

### 2.7 Online only

Offline reading would mean a local store, a sync policy, and a decision about what happens when a
module is republished under someone who downloaded it. That is a phase, not a detail.

What it must do instead is fail honestly: a clear offline state that says the connection is the
problem, never a spinner and never a half-populated screen implying content that is not there. A
quiz submitted without a connection reports that it was not recorded, because a clinician who
believes they passed and did not is worse off than one who knows to try again.

### 2.8 Reflections carry the same promise, and the phone adds a new way to break it

Phase 3 said a clinician's journal is private and Phase 4 said the assistant never reads it. The app
inherits both, and adds two hazards the browser did not have: iOS backup, and the keyboard.

Reflections are never written to disk on the device — they are edited in memory and sent — so
nothing lands in an iCloud backup, and no draft survives the app being killed. The reflection editor
disables autocorrection and spell-checking, which is what keeps the keyboard from taking client
names into the shared dictionary. That is a mitigation rather than a guarantee: short of marking the
field as secure entry, which would make a journal unusable, iOS offers no public way to opt a text
field out of keyboard learning entirely. The identifier warnings from the web port across unchanged.

## 3. Layout

```
ios/
├── Simplicity.xcworkspace/            open this, never the xcodeproj
├── Simplicity_iOS.xcodeproj/          committed
├── Simplicity_iOS/                    App, Content/, Adapters/, Info.plist, Assets
├── Packages/
│   ├── SimplicityFoundation/          extensions, Keychain, @UserDefault, .empty
│   ├── SimplicityTesting/             test base, @InjectIntoTest, fakes, hosted()
│   ├── SimplicityApi/                 Generated/ plus transport and token attachment
│   ├── SimplicityDesign/              colour, type, spacing tokens and primitives
│   ├── SimplicityServices/            @Mockable protocols, Impl/, Container
│   ├── SimplicityAuth/                sign in, sign up, confirm, reset, onboarding
│   ├── SimplicityLearn/               dashboard, module list, reader, quiz, player
│   ├── SimplicityReflect/             private journal
│   ├── SimplicityAssistant/           ask box
│   └── SimplicityAdmin/               members, teams, invitations, authoring
├── Config-Shared.xcconfig
├── Simplicity.xctestplan
├── fastlane/
└── .swiftlint.yml
```

Packages are `swift-tools-version: 6.2`, `platforms: [.iOS(.v17)]`, `swiftLanguageModes: [.v5]`,
product name equal to target name, test target `{Package}Tests`. Dependencies between them are
`.package(path:)`.

Layering runs one way. `Foundation`, `Design` and `Api` depend on nothing local; `Services` depends
on `Api` and `Foundation`; each feature package depends on `Services`, `Design` and `Foundation`,
and never on another feature package. Where two features need the same view, it moves to `Design`.
The app target depends on all of them and is depended on by none.

## 4. The app shell

`Simplicity_iOS/` holds the `@main` entry point, the root `Content` view and router, and `Adapters/`.

An adapter is the app's answer to a question a package cannot answer for itself. `SimplicityApi`
declares that something must supply a base URL and an access token but does not know where either
comes from; `AppApiAdapter` reads the base URL from the build configuration and the token from the
session. This is how `tinderbox2_ionic` keeps packages independent of the app that composes them,
and the reason its packages can be tested without an app at all.

Four tabs, matching the web: Dashboard, Learn, Reflect, Settings. The assistant is a sheet raised
from Learn rather than a fifth tab, for the reason Phase 4 gave. Administration lives under Settings
behind the same role check the web applies, and the tab does not advertise what a clinician cannot
open.

## 5. Authentication and the token path

Amplify owns the credential exchange and the Keychain. A `SessionService` in `SimplicityServices`
owns everything after it: the current user, their organisations, the active one, and their roles —
the same `/api/v1/me` shape the web caches.

No view model ever handles a token. `SimplicityApi` asks its adapter for one per request, and the
adapter asks Amplify, which refreshes when the access token is close to expiry. A 401 that survives
a refresh signs the user out, because at that point either the refresh token is exhausted or the
account has been revoked, and both mean the same thing to the person holding the phone.

Sign-up, email confirmation, password reset and invitation acceptance all mirror the web's flows.
Invitations arrive as links to the web domain; the app registers an associated domain so
`https://app.simplicityhelp.com/invitations/{token}` opens in the app when it is installed and in
Safari when it is not. That needs an `apple-app-site-association` file served from the web
distribution, which is a change to `infra/web.yaml`.

## 6. Learning and media

The module reader renders the same markdown the web does, and the sanitising rules port across
rather than being rewritten — a permissive renderer on iOS would reintroduce exactly what the web
version exists to prevent.

`GET /api/v1/orgs/{orgId}/learning/media/{assetId}/playback` already returns a playback URL, an
optional caption URL and a TTL. `AVPlayer` takes the playback URL; the WebVTT track is side-loaded
and offered through the standard subtitle control, so captions work with the system's accessibility
settings rather than a bespoke toggle. Because the URL is presigned and expires, the player refetches
when playback starts on an expired URL rather than failing.

Section completion and quiz attempts use the endpoints unchanged. Progress is the server's opinion,
never the client's: the app renders what `/learning` reports rather than tracking its own idea of
what has been read, which is what keeps it consistent with the web on the same account.

## 7. Authoring and upload

Module and quiz editing are ordinary forms over the existing draft endpoints.

Upload is the part worth designing. A video comes from `PHPickerViewController` or the camera, and
goes to S3 through the presigned multipart PUT the web already uses, on a background
`URLSession` so it survives the app being backgrounded — a several-hundred-megabyte upload over
hospital wifi will not finish while someone watches it. Progress is reported per part, the upload
can be cancelled, and `.../media/{assetId}/uploaded` is called on completion exactly as the web does
it. Transcoding is the server's business; the app polls the asset until it is `READY`, the same as
the web.

The five-hundred-megabyte cap and the one-asset-per-minute rate limit are enforced server-side
already. The app checks the size before starting, because failing a long upload at the end for a
reason knowable at the start is unkind.

## 8. Testing

Three levels, in descending order of how often they run.

`swift test` per package covers view models, services and utilities with no simulator. This is where
most of the behaviour lives and it is the reason for the package structure. Services are `@Mockable`
protocols, substituted with `@InjectIntoTest` against a `SimplicitySwiftTestingBase` that resets the
Factory container per test — the pattern `tinderbox2_ionic` arrived at.

Snapshot tests cover `SimplicityDesign` and the module reader, the two places where a silent visual
regression is plausible.

An XCUITest smoke path signs in, opens a module, and submits a quiz on a simulator. One path, not a
suite: UI tests are slow and brittle, and their value here is catching a shell that will not launch,
not asserting behaviour the unit tests already cover.

What the tests deliberately do not cover is the same class of thing that has broken in every prior
phase: the parts that only exist in AWS. A fake `AVPlayer` cannot tell us a presigned URL is
unreachable cross-origin, and a fake Cognito cannot tell us SRP is misconfigured. So the phase ends
with a run against production, through `scripts/verification.py`, which creates accounts under a
reserved prefix and deletes only what it created.

## 9. Build, signing and distribution

Xcode 26.3 with the iOS 26.2 SDK. `xcode-select` currently points at the Command Line Tools, so
either it is repointed or `DEVELOPER_DIR` is set; the fastlane lanes set it, so the build does not
depend on machine state.

fastlane provides `build`, `test` and `deploy_testflight` against Apple team `UJY6H4M6AZ`. Signing
is automatic, not `tinderbox2_ionic`'s manual profiles fed from Secrets Manager — that arrangement
serves six targets across two platforms and four configurations, and this app has one of each.
TestFlight upload uses an App Store Connect API key from `~/.private_keys`, never a password.

CI gains a macOS job building and testing the app on every push, pinned to a runner image carrying
Xcode 26 and selected explicitly rather than left on the image default, so a runner upgrade cannot
silently change the compiler. It does not upload: a build reaching TestFlight should be a deliberate
act, and the runner would need signing credentials it has no reason to hold.

Two steps need a human, both one-off: creating the App Store Connect API key, whose `.p8` can only
be downloaded once, and registering the bundle identifier `io.simplicity.training`.

## 10. Out of scope

Android, and hardening, both of which were bundled into Phase 5 and each need their own spec.
Offline reading and download. Push notifications, which need APNs, a device token table and a
sending path server-side. Biometric unlock, which is worth having and is a decision about what
re-authentication means rather than a UI detail. iPad-specific layouts — the app runs on iPad at
iPhone proportions until someone asks otherwise. Widgets, Shortcuts, Handoff, and any App Store
submission beyond TestFlight.

Worth naming rather than leaving implicit: this app does nothing the web cannot, except upload from
a camera. Its value is that a clinician has a phone in their pocket and not a laptop, which is a
distribution argument rather than a capability one, and it should not accumulate features that
widen the gap between the two clients.

## 11. Risks

| Risk | Mitigation |
| --- | --- |
| A second client drifting from the web's behaviour | Both generated from one `openapi.yaml`, guarded by the existing drift test; progress and status are read from the server, never computed locally |
| Reflection content reaching an iCloud backup or the keyboard dictionary | Never written to disk on the device; keyboard learning disabled in the reflection editor |
| A long upload failing on hospital wifi | Background `URLSession`, multipart, resumable per part, size checked before it starts |
| Presigned URLs expiring mid-session | Player refetches on expiry rather than surfacing a failure |
| iOS-only bugs escaping the suites | Production verification run at the end of the phase, as in every prior phase |
| The `.pbxproj` becoming a merge hazard | Thin app target: source files live in packages, so the project file changes only when a package is added |
| Apple review or signing blocking the phase | TestFlight only, automatic signing, and the two account steps identified up front rather than discovered at the end |
