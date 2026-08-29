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

# Every package
for p in Packages/*/; do (cd "$p" && swift test) || break; done

# The app, including the UI smoke test
xcodebuild test -workspace Simplicity.xcworkspace -scheme Simplicity_iOS \
  -destination 'platform=iOS Simulator,name=iPhone 17'

swiftlint --strict
```

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
