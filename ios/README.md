# iOS client

Native SwiftUI app for iOS 17 and later. Design: [Phase 5 — iOS](../docs/superpowers/specs/2026-08-29-phase5-ios-design.md).

## Opening it

```bash
open Simplicity.xcworkspace
```

Open the **workspace**, never `Simplicity_iOS.xcodeproj` directly.

`xcode-select` on this machine points at the Command Line Tools, so any command-line build needs:

```bash
export DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer
```

## Layout

Almost nothing lives in the app target. `Simplicity_iOS/` holds the entry point, the root view and
router, and `Adapters/` — the app's answers to questions a package cannot answer for itself, such
as where the API is and who is calling. Everything else is a local Swift package under `Packages/`.

That split is what keeps the tests fast: logic in a package runs under `swift test` in seconds with
no simulator, and only the one UI smoke path pays for a simulator boot.

Packages depend one way only. `SimplicityFoundation`, `SimplicityDesign` and `SimplicityApi` depend
on no local package; `SimplicityServices` depends on `Api` and `Foundation`; feature packages depend
on `Services`, `Design` and `Foundation`, and never on each other. Where two features need the same
view, it belongs in `SimplicityDesign`.

## Testing

```bash
# One package
cd Packages/SimplicityFoundation && swift test

# Every package, plus SwiftLint
./test-all.sh

# The app, including the UI smoke test
xcodebuild test -workspace Simplicity.xcworkspace -scheme Simplicity_iOS \
  -destination 'platform=iOS Simulator,name=iPhone 17' \
  -skipPackagePluginValidation -skipMacroValidation

swiftlint --strict
```

`test-all.sh` exists rather than a one-line loop because two things go wrong otherwise. Piping
`swift test` into a filter discards its exit code, so a package that fails to *compile* prints
nothing and reads as a pass. And adding a type to a `.package(path:)` dependency does not reliably
invalidate its dependents' incremental state, so they fail with `cannot find type 'X' in scope`
pointing at the file that plainly declares `X` — that has happened every time `SimplicityServices`
gained a service. The script detects it, clears that package's `.build`, and retries once.

Those two `-skip` flags are not optional. Amplify depends on smithy-swift, which ships a build
plugin, and Xcode refuses to run an unvalidated plugin from the command line — the build fails with
`Plugin "SmithyCodeGeneratorPlugin" must be enabled before it can be used`. Opening the workspace in
Xcode and trusting it once fixes the GUI, not `xcodebuild`.

`LiveSignInTest` skips unless given credentials, because it signs in against the **real** Cognito
pool. It is the only thing that proves Amplify's SRP implementation and the pool agree — every
other test substitutes a mock for exactly the code most likely to be wrong. Run it with a
disposable account created by [`scripts/verification.py`](../scripts/README.md), never a real one:

```bash
TEST_RUNNER_LIVE_EMAIL=verify-...@simplicityhelp.com \
TEST_RUNNER_LIVE_PASSWORD=... \
xcodebuild test -workspace Simplicity.xcworkspace -scheme Simplicity_iOS \
  -destination 'platform=iOS Simulator,name=iPhone 17' \
  -only-testing:SimplicityUITests/LiveSignInTest \
  -skipPackagePluginValidation -skipMacroValidation
```

The `TEST_RUNNER_` prefix is required: `xcodebuild` forwards only prefixed variables into the test
process on the simulator, stripping the prefix. Without it the test silently skips. Allow a
generous timeout on a cold simulator — the first launch after a build took over 60 seconds where
subsequent ones take 9.

Tests are Swift Testing, not XCTest — the exception is `SimplicityUITests`, because XCUITest has no
Swift Testing equivalent. Suites that resolve anything from the Factory container extend
`SimplicityTestCase` and are marked `@Suite(.serialized)`, since a shared container cannot be reset
safely from parallel tests.

## The project file

`Simplicity_iOS.xcodeproj` is committed and is the source of truth. `project.yml` is committed
beside it only so the project can be regenerated with `xcodegen generate` if it is ever corrupted;
nothing in the build depends on XcodeGen.

This works because the app target is thin. Adding a source file touches no project file at all —
it goes into a package. The project changes only when a whole package is added or a build setting
moves, which is rare enough to review by hand.

## Configuration

`Config-Shared.xcconfig` carries the API base URL and the Cognito pool and client, which reach the
bundle through `Info.plist`. Unlike the web, which fetches `config.json` at start-up, an app binary's
environment is fixed when it is built. None of those values are secrets — the web serves all of them
publicly.

## The API client

`Packages/SimplicityApi/Sources/SimplicityApi/Generated/` is generated from the same
`api-contract/openapi.yaml` as the Angular client, so the backend's `OpenApiSpecTest` drift check
protects both:

```bash
cd ../api-contract && npm run generate:ios
```

Never hand-edit anything under `Generated/`. It is excluded from SwiftLint for that reason.
