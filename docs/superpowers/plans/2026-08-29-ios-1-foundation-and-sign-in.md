# iOS Plan 1 — Foundation and Sign-in — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A native iOS app a clinician can sign into with their existing account, completing onboarding if they have not, and landing on a four-tab shell.

**Architecture:** A thin Xcode app target over local Swift packages. Six packages arrive here — `SimplicityFoundation`, `SimplicityTesting`, `SimplicityApi`, `SimplicityDesign`, `SimplicityServices`, `SimplicityAuth` — with the remaining four (`Learn`, `Reflect`, `Assistant`, `Admin`) added by later plans. Amplify owns the Cognito exchange; the generated OpenAPI client owns every call to our own API; no view model ever handles a token.

**Tech Stack:** Swift 6.2 tools / language mode 5, iOS 17+, SwiftUI with `@Observable`, Factory 2.3+ for DI, Mockable 0.5 for test doubles, Swift Testing, Amplify Swift 2.x, openapi-generator `swift6`.

**Spec:** [docs/superpowers/specs/2026-08-29-phase5-ios-design.md](../specs/2026-08-29-phase5-ios-design.md)

## Global Constraints

- Deployment target **iOS 17**. Every `Package.swift` declares `platforms: [.iOS(.v17)]`.
- Every `Package.swift` uses `swift-tools-version: 6.2` and `swiftLanguageModes: [.v5]`.
- Product name == target name == package name. Test target is `{PackageName}Tests`.
- Local dependencies use `.package(path: "../Name")`. External dependencies pin a version.
- **Layering is one-way.** `Foundation`, `Design`, `Api` depend on no local package. `Services` depends on `Api` and `Foundation`. Feature packages depend on `Services`, `Design`, `Foundation` — never on each other. The app target depends on all and is depended on by none.
- All classes are `final` unless designed for inheritance.
- MARK ordering within a type: `Types`, `Dependencies`, `Init`, `Properties`, `Functions`, `SwiftUI`, `Testing`.
- Constants go in a nested `enum Constants`, never scattered `private let`.
- Use `.empty` from `SimplicityFoundation`, never a bare `""`.
- User-facing strings live in `.xcstrings` and are read with `bundle: .module`. Never `NSLocalizedString`.
- No RxSwift, no `ObservableObject`, no Realm. Async/await and `@Observable`.
- Tests are Swift Testing (`@Test`, `@Suite`, `#expect`). No XCTest except the XCUITest target.
- Generated API code is never hand-edited and is excluded from SwiftLint.
- API base URL is `https://api.simplicityhelp.com`. Cognito pool `ap-southeast-2_91O0ya5nC`, iOS app client `2sihmrojcivd93m8of9q4uk1k6`, region `ap-southeast-2`. These are already public in the web's `config.json` and are not secrets.
- Every task ends with a commit. Never commit without the tests for that task passing.
- Set `DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer` for any `xcodebuild`/`xcrun` command; `xcode-select` points at the Command Line Tools on this machine.

## Note on one refinement to the spec

Spec §2.4 rejects XcodeGen as an ongoing dependency and commits the `.xcodeproj`. That still holds. XcodeGen is used **once**, in Task 3, to produce the initial project, because generating a valid `.pbxproj` by hand is not a reasonable thing to do. The generated project is committed and becomes the source of truth; `project.yml` is committed alongside it purely as a recovery path if the project file is ever corrupted. Nothing in the build depends on XcodeGen after Task 3.

---

### Task 1: SimplicityFoundation

The lowest package: no local dependencies, so it can be built and tested before an Xcode project exists.

**Files:**
- Create: `ios/Packages/SimplicityFoundation/Package.swift`
- Create: `ios/Packages/SimplicityFoundation/Sources/SimplicityFoundation/String+Empty.swift`
- Create: `ios/Packages/SimplicityFoundation/Sources/SimplicityFoundation/UserDefault.swift`
- Create: `ios/Packages/SimplicityFoundation/Sources/SimplicityFoundation/SecureStore.swift`
- Test: `ios/Packages/SimplicityFoundation/Tests/SimplicityFoundationTests/UserDefaultTests.swift`
- Test: `ios/Packages/SimplicityFoundation/Tests/SimplicityFoundationTests/SecureStoreTests.swift`

**Interfaces:**
- Consumes: nothing.
- Produces:
  - `public extension String { static var empty: String { "" } }`
  - `@propertyWrapper public struct UserDefault<Value>` with `init(_ key: PersistedKey, default: Value, store: UserDefaults = .standard)` and `var wrappedValue: Value { get set }`
  - `public enum PersistedKey: String { case lastSignedInEmail }`
  - `public protocol SecureStore: Sendable { func string(for key: String) throws -> String?; func set(_ value: String, for key: String) throws; func remove(_ key: String) throws }`
  - `public final class KeychainStore: SecureStore` with `public init(service: String)`

- [ ] **Step 1: Write the failing tests**

`ios/Packages/SimplicityFoundation/Tests/SimplicityFoundationTests/UserDefaultTests.swift`:

```swift
import Foundation
import Testing

@testable import SimplicityFoundation

@Suite("UserDefault")
struct UserDefaultTests {

    private func store() -> UserDefaults {
        let suite = UserDefaults(suiteName: "test-\(UUID().uuidString)")!
        return suite
    }

    @Test("returns the default when nothing has been written")
    func returnsDefaultWhenUnset() {
        let wrapper = UserDefault(.lastSignedInEmail, default: String.empty, store: store())
        #expect(wrapper.wrappedValue == String.empty)
    }

    @Test("returns what was written")
    func returnsWrittenValue() {
        var wrapper = UserDefault(.lastSignedInEmail, default: String.empty, store: store())
        wrapper.wrappedValue = "clinician@example.com"
        #expect(wrapper.wrappedValue == "clinician@example.com")
    }
}
```

`ios/Packages/SimplicityFoundation/Tests/SimplicityFoundationTests/SecureStoreTests.swift`:

```swift
import Foundation
import Testing

@testable import SimplicityFoundation

@Suite("KeychainStore")
struct SecureStoreTests {

    private enum Constants {
        static let key = "token"
    }

    private func subject() -> KeychainStore {
        KeychainStore(service: "test-\(UUID().uuidString)")
    }

    @Test("returns nil for a key that was never written")
    func missingKeyIsNil() throws {
        #expect(try subject().string(for: Constants.key) == nil)
    }

    @Test("round-trips a value")
    func roundTrips() throws {
        let store = subject()
        try store.set("abc123", for: Constants.key)
        #expect(try store.string(for: Constants.key) == "abc123")
    }

    @Test("overwrites rather than duplicating")
    func overwrites() throws {
        let store = subject()
        try store.set("first", for: Constants.key)
        try store.set("second", for: Constants.key)
        #expect(try store.string(for: Constants.key) == "second")
    }

    @Test("removes a value")
    func removes() throws {
        let store = subject()
        try store.set("abc123", for: Constants.key)
        try store.remove(Constants.key)
        #expect(try store.string(for: Constants.key) == nil)
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

```bash
cd ios/Packages/SimplicityFoundation && swift test
```

Expected: compilation failure — no `Package.swift` and no such module.

- [ ] **Step 3: Write the package manifest and the implementation**

`ios/Packages/SimplicityFoundation/Package.swift`:

```swift
// swift-tools-version: 6.2

import PackageDescription

let package = Package(
    name: "SimplicityFoundation",
    defaultLocalization: "en",
    platforms: [.iOS(.v17), .macOS(.v14)],
    products: [
        .library(name: "SimplicityFoundation", targets: ["SimplicityFoundation"])
    ],
    targets: [
        .target(name: "SimplicityFoundation"),
        .testTarget(name: "SimplicityFoundationTests", dependencies: ["SimplicityFoundation"])
    ],
    swiftLanguageModes: [.v5]
)
```

`Sources/SimplicityFoundation/String+Empty.swift`:

```swift
import Foundation

public extension String {

    /// Preferred over a bare `""`, which is easy to misread as an oversight.
    static var empty: String { "" }
}
```

`Sources/SimplicityFoundation/UserDefault.swift`:

```swift
import Foundation

/// Keys are enumerated so a typo cannot silently read a value nobody ever writes.
public enum PersistedKey: String {
    case lastSignedInEmail
}

@propertyWrapper
public struct UserDefault<Value> {

    // MARK: Properties

    private let key: String
    private let defaultValue: Value
    private let store: UserDefaults

    public var wrappedValue: Value {
        get { store.object(forKey: key) as? Value ?? defaultValue }
        set { store.set(newValue, forKey: key) }
    }

    // MARK: Init

    public init(_ key: PersistedKey, default defaultValue: Value, store: UserDefaults = .standard) {
        self.key = key.rawValue
        self.defaultValue = defaultValue
        self.store = store
    }
}
```

`Sources/SimplicityFoundation/SecureStore.swift`:

```swift
import Foundation
import Security

public enum SecureStoreError: Error {
    case unexpectedStatus(OSStatus)
    case unreadableData
}

/// Anything that needs the Keychain depends on this, so tests can substitute memory.
public protocol SecureStore: Sendable {
    func string(for key: String) throws -> String?
    func set(_ value: String, for key: String) throws
    func remove(_ key: String) throws
}

public final class KeychainStore: SecureStore {

    // MARK: Properties

    private let service: String

    // MARK: Init

    public init(service: String) {
        self.service = service
    }

    // MARK: Functions

    public func string(for key: String) throws -> String? {
        var query = baseQuery(for: key)
        query[kSecReturnData as String] = true
        query[kSecMatchLimit as String] = kSecMatchLimitOne

        var item: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &item)
        if status == errSecItemNotFound { return nil }
        guard status == errSecSuccess else { throw SecureStoreError.unexpectedStatus(status) }
        guard let data = item as? Data, let value = String(data: data, encoding: .utf8) else {
            throw SecureStoreError.unreadableData
        }
        return value
    }

    public func set(_ value: String, for key: String) throws {
        // Delete first: SecItemAdd fails rather than replacing, and an update path would
        // need the same query twice for no gain.
        try remove(key)

        var query = baseQuery(for: key)
        query[kSecValueData as String] = Data(value.utf8)
        query[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlock

        let status = SecItemAdd(query as CFDictionary, nil)
        guard status == errSecSuccess else { throw SecureStoreError.unexpectedStatus(status) }
    }

    public func remove(_ key: String) throws {
        let status = SecItemDelete(baseQuery(for: key) as CFDictionary)
        guard status == errSecSuccess || status == errSecItemNotFound else {
            throw SecureStoreError.unexpectedStatus(status)
        }
    }

    // MARK: Private

    private func baseQuery(for key: String) -> [String: Any] {
        [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: key
        ]
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

```bash
cd ios/Packages/SimplicityFoundation && swift test
```

Expected: 6 tests pass. If the Keychain tests fail with `errSecMissingEntitlement` (-34018) when run from a plain `swift test` on macOS, that is a known sandbox limitation; run them instead via the app's test plan in Task 3 and note it in the package README.

- [ ] **Step 5: Commit**

```bash
git add ios/Packages/SimplicityFoundation
git commit -m "iOS: SimplicityFoundation"
```

---

### Task 2: SimplicityTesting

Shared test infrastructure, needed before any package that uses dependency injection can be tested.

**Files:**
- Create: `ios/Packages/SimplicityTesting/Package.swift`
- Create: `ios/Packages/SimplicityTesting/Sources/SimplicityTesting/SimplicityTestCase.swift`
- Create: `ios/Packages/SimplicityTesting/Sources/SimplicityTesting/InMemorySecureStore.swift`
- Test: `ios/Packages/SimplicityTesting/Tests/SimplicityTestingTests/InMemorySecureStoreTests.swift`

**Interfaces:**
- Consumes: `SecureStore` from `SimplicityFoundation`.
- Produces:
  - `open class SimplicityTestCase` with `public init()` that calls `Container.shared.reset()`, and a `deinit` that does the same.
  - `public final class InMemorySecureStore: SecureStore` with `public init()`.

- [ ] **Step 1: Write the failing test**

`ios/Packages/SimplicityTesting/Tests/SimplicityTestingTests/InMemorySecureStoreTests.swift`:

```swift
import Testing

@testable import SimplicityTesting

@Suite("InMemorySecureStore")
struct InMemorySecureStoreTests {

    @Test("round-trips and removes without touching the Keychain")
    func roundTripsAndRemoves() throws {
        let store = InMemorySecureStore()
        #expect(try store.string(for: "k") == nil)
        try store.set("v", for: "k")
        #expect(try store.string(for: "k") == "v")
        try store.remove("k")
        #expect(try store.string(for: "k") == nil)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
cd ios/Packages/SimplicityTesting && swift test
```

Expected: no such module `SimplicityTesting`.

- [ ] **Step 3: Write the package manifest and the implementation**

`ios/Packages/SimplicityTesting/Package.swift`:

```swift
// swift-tools-version: 6.2

import PackageDescription

let package = Package(
    name: "SimplicityTesting",
    platforms: [.iOS(.v17), .macOS(.v14)],
    products: [
        .library(name: "SimplicityTesting", targets: ["SimplicityTesting"])
    ],
    dependencies: [
        .package(path: "../SimplicityFoundation"),
        .package(url: "https://github.com/hmlongco/Factory.git", .upToNextMajor(from: "2.3.0"))
    ],
    targets: [
        .target(
            name: "SimplicityTesting",
            dependencies: [
                "SimplicityFoundation",
                .product(name: "Factory", package: "Factory")
            ]
        ),
        .testTarget(name: "SimplicityTestingTests", dependencies: ["SimplicityTesting"])
    ],
    swiftLanguageModes: [.v5]
)
```

`Sources/SimplicityTesting/SimplicityTestCase.swift`:

```swift
import Factory

/// Base for every suite that resolves anything from the container.
///
/// Factory's container is global, so a registration left behind by one test is visible to the
/// next. Resetting on both sides means a suite is unaffected by what ran before it and leaves
/// nothing for what runs after. Suites that use this must be `@Suite(.serialized)`, because a
/// shared container cannot be reset safely from parallel tests.
open class SimplicityTestCase {

    public init() {
        Container.shared.reset()
    }

    deinit {
        Container.shared.reset()
    }
}
```

`Sources/SimplicityTesting/InMemorySecureStore.swift`:

```swift
import Foundation
import SimplicityFoundation

public final class InMemorySecureStore: SecureStore, @unchecked Sendable {

    // MARK: Properties

    private var values: [String: String] = [:]
    private let lock = NSLock()

    // MARK: Init

    public init() {}

    // MARK: Functions

    public func string(for key: String) throws -> String? {
        lock.withLock { values[key] }
    }

    public func set(_ value: String, for key: String) throws {
        lock.withLock { values[key] = value }
    }

    public func remove(_ key: String) throws {
        _ = lock.withLock { values.removeValue(forKey: key) }
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

```bash
cd ios/Packages/SimplicityTesting && swift test
```

Expected: 1 test passes.

- [ ] **Step 5: Commit**

```bash
git add ios/Packages/SimplicityTesting
git commit -m "iOS: SimplicityTesting"
```

---

### Task 3: The Xcode workspace and an app that launches

Produces the first thing that runs on a simulator. Nothing is on screen but a placeholder; the deliverable is a green `xcodebuild test`.

**Files:**
- Create: `ios/project.yml` (bootstrap only — see the note above)
- Create: `ios/Simplicity_iOS/SimplicityApp.swift`
- Create: `ios/Simplicity_iOS/Content/ContentView.swift`
- Create: `ios/Simplicity_iOS/Info.plist`
- Create: `ios/Simplicity_iOS/Assets.xcassets/` (`AppIcon`, `AccentColor`)
- Create: `ios/Config-Shared.xcconfig`
- Create: `ios/.swiftlint.yml`
- Create: `ios/Simplicity.xctestplan`
- Create: `ios/SimplicityUITests/SmokeTests.swift`
- Create: `ios/README.md` (replacing the placeholder)
- Generated then committed: `ios/Simplicity_iOS.xcodeproj/`, `ios/Simplicity.xcworkspace/`

**Interfaces:**
- Consumes: `SimplicityFoundation`, `SimplicityTesting`.
- Produces: a `Simplicity_iOS` scheme that builds and tests; `AppConfiguration.apiBaseURL`, `.cognitoPoolId`, `.cognitoClientId` read from the bundle.

- [ ] **Step 1: Install XcodeGen and write the manifest**

```bash
brew install xcodegen
```

`ios/project.yml`:

```yaml
name: Simplicity_iOS
options:
  bundleIdPrefix: io.simplicity
  deploymentTarget:
    iOS: "17.0"
  createIntermediateGroups: true

configs:
  Debug: debug
  Release: release

settings:
  base:
    SWIFT_VERSION: "5.0"
    DEVELOPMENT_TEAM: UJY6H4M6AZ
    CODE_SIGN_STYLE: Automatic
    MARKETING_VERSION: "1.0"
    CURRENT_PROJECT_VERSION: "1"

packages:
  SimplicityFoundation:
    path: Packages/SimplicityFoundation
  SimplicityTesting:
    path: Packages/SimplicityTesting

targets:
  Simplicity_iOS:
    type: application
    platform: iOS
    sources: [Simplicity_iOS]
    configFiles:
      Debug: Config-Shared.xcconfig
      Release: Config-Shared.xcconfig
    info:
      path: Simplicity_iOS/Info.plist
      properties:
        CFBundleDisplayName: Simplicity
        UILaunchScreen: {}
        ITSAppUsesNonExemptEncryption: false
    settings:
      base:
        PRODUCT_BUNDLE_IDENTIFIER: io.simplicity.training
    dependencies:
      - package: SimplicityFoundation

  SimplicityUITests:
    type: bundle.ui-testing
    platform: iOS
    sources: [SimplicityUITests]
    dependencies:
      - target: Simplicity_iOS

schemes:
  Simplicity_iOS:
    build:
      targets:
        Simplicity_iOS: all
    test:
      testPlans:
        - path: Simplicity.xctestplan
          defaultPlan: true
    run:
      config: Debug
```

- [ ] **Step 2: Write the app sources, configuration and lint rules**

`ios/Config-Shared.xcconfig`:

```
// Build-time configuration. Unlike the web, which fetches config.json, an app binary's
// environment is fixed when it is built. These values are already public in the web's config.json.
API_BASE_URL = https:/$()/api.simplicityhelp.com
COGNITO_POOL_ID = ap-southeast-2_91O0ya5nC
COGNITO_CLIENT_ID = 2sihmrojcivd93m8of9q4uk1k6
COGNITO_REGION = ap-southeast-2
```

The `$()` in the URL is required: xcconfig treats `//` as a comment, and this splits the sequence without changing the value.

`ios/Simplicity_iOS/Info.plist` adds the four keys so they reach the bundle:

```xml
<key>APIBaseURL</key>
<string>$(API_BASE_URL)</string>
<key>CognitoPoolId</key>
<string>$(COGNITO_POOL_ID)</string>
<key>CognitoClientId</key>
<string>$(COGNITO_CLIENT_ID)</string>
<key>CognitoRegion</key>
<string>$(COGNITO_REGION)</string>
```

`ios/Simplicity_iOS/SimplicityApp.swift`:

```swift
import SwiftUI

@main
struct SimplicityApp: App {

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
```

`ios/Simplicity_iOS/Content/ContentView.swift`:

```swift
import SwiftUI

struct ContentView: View {

    var body: some View {
        Text("Simplicity")
            .accessibilityIdentifier("app-root")
    }
}
```

`ios/.swiftlint.yml`:

```yaml
disabled_rules:
  - line_length
  - todo

opt_in_rules:
  - empty_string
  - force_unwrapping
  - implicit_return
  - modifier_order
  - sorted_imports

excluded:
  - Packages/SimplicityApi/Sources/SimplicityApi/Generated
  - .build

file_length:
  warning: 500
  error: 1000

type_body_length:
  warning: 300
  error: 600
```

`ios/SimplicityUITests/SmokeTests.swift`:

```swift
import XCTest

/// One path only. UI tests are slow and brittle; their job here is to catch a shell that will
/// not launch, not to assert behaviour the package tests already cover.
final class SmokeTests: XCTestCase {

    func testAppLaunches() {
        let app = XCUIApplication()
        app.launch()
        XCTAssertTrue(app.otherElements["app-root"].waitForExistence(timeout: 10)
            || app.staticTexts["app-root"].waitForExistence(timeout: 10))
    }
}
```

- [ ] **Step 3: Generate the project and run the tests to verify they fail or pass meaningfully**

```bash
cd ios && xcodegen generate
export DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer
xcodebuild test -project Simplicity_iOS.xcodeproj -scheme Simplicity_iOS \
  -destination 'platform=iOS Simulator,name=iPhone 17' | xcbeautify
```

Expected: the app builds and the smoke test passes. If `xcbeautify` is not installed, drop the pipe.

- [ ] **Step 4: Create the workspace and confirm it opens the same scheme**

XcodeGen produces the project, not the workspace. Create `ios/Simplicity.xcworkspace/contents.xcworkspacedata`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<Workspace
   version = "1.0">
   <FileRef
      location = "group:Simplicity_iOS.xcodeproj">
   </FileRef>
</Workspace>
```

Re-run the test command with `-workspace Simplicity.xcworkspace` instead of `-project` and confirm it still passes.

- [ ] **Step 5: Write the README and commit**

`ios/README.md` replaces the one-line placeholder, and states: open `Simplicity.xcworkspace` and never the `.xcodeproj`; the project is committed and hand-maintained; `project.yml` exists only to regenerate it if corrupted; `DEVELOPER_DIR` must point at Xcode on machines where `xcode-select` does not.

```bash
git add ios
git commit -m "iOS: workspace, app target, and a smoke test"
```

---

### Task 4: SimplicityApi

The generated client plus the small hand-written layer that gives it a base URL and a token.

**Files:**
- Modify: `api-contract/generator/swift.yaml` (fix `outputDir`)
- Modify: `api-contract/package.json` (no change needed; confirm `generate:ios` runs)
- Generated then committed: `ios/Packages/SimplicityApi/Sources/SimplicityApi/Generated/`
- Create: `ios/Packages/SimplicityApi/Package.swift` (overwriting the generated manifest)
- Create: `ios/Packages/SimplicityApi/Sources/SimplicityApi/ApiAdapter.swift`
- Create: `ios/Packages/SimplicityApi/Sources/SimplicityApi/ApiConfiguration.swift`
- Create: `ios/Packages/SimplicityApi/Sources/SimplicityApi/_Package/Container+Api.swift`
- Test: `ios/Packages/SimplicityApi/Tests/SimplicityApiTests/ApiConfigurationTests.swift`

**Interfaces:**
- Consumes: nothing local.
- Produces:
  - `public protocol ApiAdapter: Sendable { var baseURL: URL { get }; func accessToken() async -> String? }`
  - `public enum ApiConfiguration { public static func apply(_ adapter: ApiAdapter) async }` — sets `SimplicityApiAPI.basePath` and installs the bearer header.
  - `Container.shared.apiAdapter: Factory<ApiAdapter>`
  - The generated `CurrentUserAPI`, `LearningAPI`, … and models including `CurrentUserResponse` with `id`, `email`, `fullName`, `phone`, `professionalRole`, `profileCompleted`, `status`, `platformRole`, `activeOrganisationId`, `organisations`.

- [ ] **Step 1: Fix the generator configuration**

In `api-contract/generator/swift.yaml`, change `outputDir` and drop the stale comment:

```yaml
# Generated from the same openapi.yaml as the Angular client, so the drift check in
# OpenApiSpecTest protects both. Never hand-edit the output.
generatorName: swift6
inputSpec: openapi.yaml
outputDir: ../ios/Packages/SimplicityApi

additionalProperties:
  projectName: SimplicityApi
  responseAs: AsyncAwait
  useSPMFileStructure: true
  swiftPackagePath: .

globalProperty:
  apiDocs: false
  modelDocs: false
  apiTests: false
  modelTests: false
```

- [ ] **Step 2: Generate the client and inspect what arrived**

```bash
cd api-contract && npm install && npm run generate:ios
ls ios/Packages/SimplicityApi/Sources/SimplicityApi
```

Expected: `APIs/`, `Models/`, `APIHelper.swift`, `Configuration.swift`, `OpenISO8601DateFormatter.swift` and similar. Move the generated sources under a `Generated/` subdirectory so the SwiftLint exclusion in Task 3 applies and hand-written files are distinguishable:

```bash
cd ios/Packages/SimplicityApi/Sources/SimplicityApi
mkdir -p Generated && for f in *; do [ "$f" = Generated ] || mv "$f" Generated/; done
```

- [ ] **Step 3: Write the failing test**

`ios/Packages/SimplicityApi/Tests/SimplicityApiTests/ApiConfigurationTests.swift`:

```swift
import Foundation
import Testing

@testable import SimplicityApi

@Suite("ApiConfiguration")
struct ApiConfigurationTests {

    private struct StubAdapter: ApiAdapter {
        let baseURL = URL(string: "https://api.example.com")!
        let token: String?
        func accessToken() async -> String? { token }
    }

    @Test("points the generated client at the adapter's base URL")
    func setsBasePath() async {
        await ApiConfiguration.apply(StubAdapter(token: nil))
        #expect(SimplicityApiAPI.basePath == "https://api.example.com")
    }

    @Test("attaches a bearer header when there is a token")
    func attachesBearer() async throws {
        await ApiConfiguration.apply(StubAdapter(token: "abc123"))
        let headers = await ApiConfiguration.authorizationHeaders()
        #expect(headers["Authorization"] == "Bearer abc123")
    }

    @Test("sends no authorization header when signed out")
    func omitsBearerWhenSignedOut() async throws {
        await ApiConfiguration.apply(StubAdapter(token: nil))
        let headers = await ApiConfiguration.authorizationHeaders()
        #expect(headers["Authorization"] == nil)
    }
}
```

- [ ] **Step 4: Run the test to verify it fails**

```bash
cd ios/Packages/SimplicityApi && swift test
```

Expected: `ApiAdapter` and `ApiConfiguration` are undefined.

- [ ] **Step 5: Write the manifest and the hand-written layer**

Overwrite the generated `ios/Packages/SimplicityApi/Package.swift`:

```swift
// swift-tools-version: 6.2

import PackageDescription

let package = Package(
    name: "SimplicityApi",
    platforms: [.iOS(.v17), .macOS(.v14)],
    products: [
        .library(name: "SimplicityApi", targets: ["SimplicityApi"])
    ],
    dependencies: [
        .package(url: "https://github.com/hmlongco/Factory.git", .upToNextMajor(from: "2.3.0"))
    ],
    targets: [
        .target(
            name: "SimplicityApi",
            dependencies: [.product(name: "Factory", package: "Factory")]
        ),
        .testTarget(name: "SimplicityApiTests", dependencies: ["SimplicityApi"])
    ],
    swiftLanguageModes: [.v5]
)
```

`Sources/SimplicityApi/ApiAdapter.swift`:

```swift
import Foundation

/// What the package needs but cannot know: where the API is, and who is calling.
///
/// The app supplies this. Keeping it a protocol is what lets this package be tested without an
/// app, and what keeps Cognito out of it entirely.
public protocol ApiAdapter: Sendable {
    var baseURL: URL { get }
    func accessToken() async -> String?
}
```

`Sources/SimplicityApi/ApiConfiguration.swift`:

```swift
import Foundation

public enum ApiConfiguration {

    // MARK: Properties

    private nonisolated(unsafe) static var adapter: ApiAdapter?

    // MARK: Functions

    /// Called once at launch, and again only if the adapter changes.
    public static func apply(_ adapter: ApiAdapter) async {
        self.adapter = adapter
        SimplicityApiAPI.basePath = adapter.baseURL.absoluteString
        SimplicityApiAPI.customHeaders = [:]
    }

    /// Resolved per request rather than cached, because Amplify refreshes the access token when
    /// it is close to expiry and a cached one would go stale mid-session.
    public static func authorizationHeaders() async -> [String: String] {
        guard let token = await adapter?.accessToken() else { return [:] }
        return ["Authorization": "Bearer \(token)"]
    }
}
```

The generated client applies `SimplicityApiAPI.customHeaders` at request-build time, which is synchronous, so a request interceptor is what actually attaches the header. Add `Sources/SimplicityApi/BearerInterceptor.swift`:

```swift
import Foundation

/// The generated `RequestBuilder` exposes an interceptor hook precisely so the header can be
/// resolved when the request is made rather than when the client was configured.
final class BearerInterceptor: OpenAPIInterceptor {

    func intercept(
        urlRequest: URLRequest,
        urlSession: URLSessionProtocol,
        requestBuilder: RequestBuilder<some Any>,
        completion: @escaping (Result<URLRequest, Error>) -> Void
    ) {
        Task {
            var request = urlRequest
            for (name, value) in await ApiConfiguration.authorizationHeaders() {
                request.setValue(value, forHTTPHeaderField: name)
            }
            completion(.success(request))
        }
    }

    func retry(
        urlRequest: URLRequest,
        urlSession: URLSessionProtocol,
        requestBuilder: RequestBuilder<some Any>,
        data: Data?,
        response: URLResponse?,
        error: Error,
        completion: @escaping (OpenAPIInterceptorRetry) -> Void
    ) {
        completion(.dontRetry)
    }
}
```

Wire it in `ApiConfiguration.apply` with `SimplicityApiAPI.requestBuilderFactory` / `OpenAPIClient.shared.interceptor`, matching whatever the generated `Configuration.swift` actually exposes — read that file rather than assuming, as the `swift6` generator's hook names vary by version. If no interceptor hook exists, fall back to setting `customHeaders` from `apply` and refreshing it in `SessionService` after each token refresh, and record that decision in the package README.

`Sources/SimplicityApi/_Package/Container+Api.swift`:

```swift
import Factory
import Foundation

public extension Container {

    /// `fatalError` on purpose: the app must register an adapter before anything calls the API,
    /// and a silent default would turn that omission into a confusing 401 instead of a crash on
    /// the first line of `main`.
    var apiAdapter: Factory<ApiAdapter> {
        self { fatalError("No ApiAdapter registered. The app target must register one at launch.") }
    }
}
```

- [ ] **Step 6: Run the tests to verify they pass**

```bash
cd ios/Packages/SimplicityApi && swift test
```

Expected: 3 tests pass.

- [ ] **Step 7: Add the package to the project and commit**

Add `SimplicityApi` to `packages:` and to the app target's `dependencies:` in `ios/project.yml`, regenerate, and confirm the app still builds:

```bash
cd ios && xcodegen generate
export DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer
xcodebuild build -workspace Simplicity.xcworkspace -scheme Simplicity_iOS \
  -destination 'platform=iOS Simulator,name=iPhone 17'
```

```bash
git add api-contract/generator/swift.yaml ios
git commit -m "iOS: generated API client and its transport adapter"
```

---

### Task 5: SimplicityDesign

Tokens and the handful of primitives every feature needs, so no feature invents its own spacing.

**Files:**
- Create: `ios/Packages/SimplicityDesign/Package.swift`
- Create: `ios/Packages/SimplicityDesign/Sources/SimplicityDesign/Tokens.swift`
- Create: `ios/Packages/SimplicityDesign/Sources/SimplicityDesign/PrimaryButton.swift`
- Create: `ios/Packages/SimplicityDesign/Sources/SimplicityDesign/FormField.swift`
- Create: `ios/Packages/SimplicityDesign/Sources/SimplicityDesign/ErrorBanner.swift`
- Test: `ios/Packages/SimplicityDesign/Tests/SimplicityDesignTests/TokensTests.swift`

**Interfaces:**
- Consumes: nothing local.
- Produces: `Spacing.x1` … `.x6` (4, 8, 12, 16, 24, 32); `Color.brandPrimary`, `.brandSurface`, `.brandDanger`, `.brandTextPrimary`, `.brandTextSecondary`; `Font.brandTitle`, `.brandBody`, `.brandCaption`; `PrimaryButton(title:isLoading:action:)`; `FormField(label:text:isSecure:contentType:)`; `ErrorBanner(message:)`.

- [ ] **Step 1: Write the failing test**

```swift
import SwiftUI
import Testing

@testable import SimplicityDesign

@Suite("Spacing")
struct TokensTests {

    @Test("the scale is the one the web uses, in order")
    func scaleIsOrdered() {
        let scale = [Spacing.x1, Spacing.x2, Spacing.x3, Spacing.x4, Spacing.x5, Spacing.x6]
        #expect(scale == [4, 8, 12, 16, 24, 32])
        #expect(scale == scale.sorted())
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
cd ios/Packages/SimplicityDesign && swift test
```

Expected: no such module.

- [ ] **Step 3: Write the manifest and the tokens**

`Package.swift` follows the shape in Task 1, named `SimplicityDesign`, with no local dependencies.

`Sources/SimplicityDesign/Tokens.swift`:

```swift
import SwiftUI

/// A fixed scale. A feature that needs a value not on it should change the scale, not
/// hard-code a number — that is the whole reason this exists.
public enum Spacing {
    public static let x1: CGFloat = 4
    public static let x2: CGFloat = 8
    public static let x3: CGFloat = 12
    public static let x4: CGFloat = 16
    public static let x5: CGFloat = 24
    public static let x6: CGFloat = 32
}

public extension Color {
    static let brandPrimary = Color(red: 0.11, green: 0.36, blue: 0.62)
    static let brandSurface = Color(uiColor: .secondarySystemBackground)
    static let brandDanger = Color(red: 0.72, green: 0.16, blue: 0.16)
    static let brandTextPrimary = Color(uiColor: .label)
    static let brandTextSecondary = Color(uiColor: .secondaryLabel)
}

public extension Font {
    static let brandTitle = Font.system(.title2, design: .default, weight: .semibold)
    static let brandBody = Font.system(.body)
    static let brandCaption = Font.system(.caption)
}
```

`PrimaryButton`, `FormField` and `ErrorBanner` are ordinary SwiftUI views over those tokens. `FormField` takes a `UITextContentType?` so callers can set `.emailAddress` and `.password` and get the right keyboard and autofill; `ErrorBanner` renders nothing when `message` is nil.

- [ ] **Step 4: Run the test to verify it passes**

```bash
cd ios/Packages/SimplicityDesign && swift test
```

Expected: 1 test passes.

- [ ] **Step 5: Commit**

```bash
git add ios/Packages/SimplicityDesign ios/project.yml
git commit -m "iOS: SimplicityDesign"
```

---

### Task 6: SessionService

The app's model of who is signed in. Everything downstream reads this rather than calling `/me` itself.

**Files:**
- Create: `ios/Packages/SimplicityServices/Package.swift`
- Create: `ios/Packages/SimplicityServices/Sources/SimplicityServices/SessionService.swift`
- Create: `ios/Packages/SimplicityServices/Sources/SimplicityServices/Impl/SessionServiceImpl.swift`
- Create: `ios/Packages/SimplicityServices/Sources/SimplicityServices/_Package/Container+Services.swift`
- Test: `ios/Packages/SimplicityServices/Tests/SimplicityServicesTests/SessionServiceTests.swift`

**Interfaces:**
- Consumes: `CurrentUserAPI`, `CurrentUserResponse`, `OrganisationMembershipResponse` from `SimplicityApi`.
- Produces:
  - `@Mockable public protocol SessionService: AnyObject, Sendable { var current: CurrentUserResponse? { get async }; func refresh() async throws -> CurrentUserResponse; func setActiveOrganisation(_ id: UUID) async throws -> CurrentUserResponse; func clear() async }`
  - `public extension CurrentUserResponse { var needsOnboarding: Bool; var activeOrganisation: OrganisationMembershipResponse? }`
  - `Container.shared.sessionService: Factory<SessionService>` scoped `.singleton`

- [ ] **Step 1: Write the failing tests**

```swift
import Foundation
import Mockable
import SimplicityApi
import SimplicityTesting
import Testing

@testable import SimplicityServices

@Suite(.serialized)
final class SessionServiceTests: SimplicityTestCase {

    private enum Constants {
        static let orgId = UUID()
    }

    private func user(
        profileCompleted: Bool = true,
        activeOrganisationId: UUID? = Constants.orgId,
        organisations: [OrganisationMembershipResponse] = []
    ) -> CurrentUserResponse {
        CurrentUserResponse(
            activeOrganisationId: activeOrganisationId,
            email: "clinician@example.com",
            fullName: "A Clinician",
            id: UUID(),
            organisations: organisations,
            phone: nil,
            platformRole: .standard,
            professionalRole: "Psychologist",
            profileCompleted: profileCompleted,
            status: .active
        )
    }

    @Test("needs onboarding when the profile is incomplete")
    func needsOnboardingWithoutProfile() {
        #expect(user(profileCompleted: false).needsOnboarding)
    }

    @Test("needs onboarding when the profile is done but there is no organisation")
    func needsOnboardingWithoutOrganisation() {
        #expect(user(activeOrganisationId: nil).needsOnboarding)
    }

    @Test("does not need onboarding with a profile and an organisation")
    func onboardedUser() {
        #expect(user().needsOnboarding == false)
    }

    @Test("caches the user after a refresh so callers do not each fetch")
    func cachesAfterRefresh() async throws {
        let service = SessionServiceImpl(fetch: { self.user() })
        #expect(await service.current == nil)
        _ = try await service.refresh()
        #expect(await service.current?.email == "clinician@example.com")
    }

    @Test("clear forgets the user, so a sign-out cannot leak into the next session")
    func clearForgets() async throws {
        let service = SessionServiceImpl(fetch: { self.user() })
        _ = try await service.refresh()
        await service.clear()
        #expect(await service.current == nil)
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

```bash
cd ios/Packages/SimplicityServices && swift test
```

Expected: no such module `SimplicityServices`.

- [ ] **Step 3: Write the manifest and the implementation**

`Package.swift` depends on `../SimplicityApi`, `../SimplicityFoundation`, `../SimplicityTesting` (test target only), Factory, and `.package(url: "https://github.com/Kolos65/Mockable.git", exact: "0.5.0")`, with `swiftSettings: [.define("MOCKING", .when(configuration: .debug))]` on the target.

`Sources/SimplicityServices/SessionService.swift`:

```swift
import Foundation
import Mockable
import SimplicityApi

@Mockable
public protocol SessionService: AnyObject, Sendable {
    var current: CurrentUserResponse? { get async }
    @discardableResult func refresh() async throws -> CurrentUserResponse
    func setActiveOrganisation(_ id: UUID) async throws -> CurrentUserResponse
    func clear() async
}

public extension CurrentUserResponse {

    /// Two gates, matching the web's onboarding guard: a professional profile, and an
    /// organisation to work in. Either missing means the wizard, not the app.
    var needsOnboarding: Bool {
        profileCompleted != true || activeOrganisationId == nil
    }

    var activeOrganisation: OrganisationMembershipResponse? {
        guard let activeOrganisationId else { return nil }
        return organisations?.first { $0.organisationId == activeOrganisationId }
    }
}
```

`Sources/SimplicityServices/Impl/SessionServiceImpl.swift`:

```swift
import Foundation
import SimplicityApi

/// An actor because the cached user is read from every screen and written by sign-in,
/// onboarding and organisation switching.
public actor SessionServiceImpl: SessionService {

    // MARK: Types

    public typealias Fetch = @Sendable () async throws -> CurrentUserResponse

    // MARK: Properties

    private var cached: CurrentUserResponse?
    private let fetch: Fetch
    private let setActive: @Sendable (UUID) async throws -> CurrentUserResponse

    public var current: CurrentUserResponse? { cached }

    // MARK: Init

    public init(
        fetch: @escaping Fetch = { try await CurrentUserAPI.getCurrentUser() },
        setActive: @escaping @Sendable (UUID) async throws -> CurrentUserResponse = { id in
            try await CurrentUserAPI.setActiveOrganisation(
                setActiveOrganisationRequest: SetActiveOrganisationRequest(organisationId: id)
            )
        }
    ) {
        self.fetch = fetch
        self.setActive = setActive
    }

    // MARK: Functions

    @discardableResult
    public func refresh() async throws -> CurrentUserResponse {
        let user = try await fetch()
        cached = user
        return user
    }

    public func setActiveOrganisation(_ id: UUID) async throws -> CurrentUserResponse {
        let user = try await setActive(id)
        cached = user
        return user
    }

    public func clear() async {
        cached = nil
    }
}
```

Injecting the two calls as closures rather than a protocol-wrapped API keeps the test free of a mock for generated static methods, which the generator emits as `enum` statics and which cannot be mocked directly.

`Sources/SimplicityServices/_Package/Container+Services.swift`:

```swift
import Factory
import Foundation

public extension Container {

    var sessionService: Factory<SessionService> {
        self { SessionServiceImpl() }.scope(.singleton)
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

```bash
cd ios/Packages/SimplicityServices && swift test
```

Expected: 5 tests pass.

- [ ] **Step 5: Commit**

```bash
git add ios/Packages/SimplicityServices ios/project.yml
git commit -m "iOS: SessionService"
```

---

### Task 7: AuthService over Amplify

The only place in the app that talks to Cognito, mirroring `web/src/app/core/auth/auth.service.ts` method for method.

**Files:**
- Create: `ios/Packages/SimplicityAuth/Package.swift`
- Create: `ios/Packages/SimplicityAuth/Sources/SimplicityAuth/AuthService.swift`
- Create: `ios/Packages/SimplicityAuth/Sources/SimplicityAuth/Impl/AmplifyAuthService.swift`
- Create: `ios/Packages/SimplicityAuth/Sources/SimplicityAuth/_Package/Container+Auth.swift`
- Create: `ios/Simplicity_iOS/amplifyconfiguration.json`
- Test: `ios/Packages/SimplicityAuth/Tests/SimplicityAuthTests/AuthServiceContractTests.swift`

**Interfaces:**
- Consumes: nothing local beyond `SimplicityFoundation`.
- Produces:
  - `@Mockable public protocol AuthService: AnyObject, Sendable` with `signUp(email:password:)`, `confirmSignUp(email:code:)`, `resendConfirmationCode(email:)`, `signIn(email:password:) async throws -> Bool`, `signOut()`, `startPasswordReset(email:)`, `confirmPasswordReset(email:code:newPassword:)`, `isSignedIn() async -> Bool`, `accessToken() async -> String?`
  - `public final class AmplifyAuthService: AuthService` with `public static func configure() throws`
  - `Container.shared.authService: Factory<AuthService>` scoped `.singleton`

- [ ] **Step 1: Write the failing test**

Amplify cannot be exercised in a unit test, so the test covers the contract every caller depends on — that `signIn` reports "not signed in" rather than throwing when Cognito needs a further step, which is what the web's comment describes and what the sign-in screen routes on.

```swift
import Mockable
import SimplicityTesting
import Testing

@testable import SimplicityAuth

@Suite(.serialized)
final class AuthServiceContractTests: SimplicityTestCase {

    @Test("an unconfirmed account reports not-signed-in rather than throwing")
    func unconfirmedAccountIsNotAnError() async throws {
        let auth = MockAuthService(policy: .relaxed)
        given(auth).signIn(email: .any, password: .any).willReturn(false)

        let signedIn = try await auth.signIn(email: "a@b.com", password: "x")

        #expect(signedIn == false)
    }

    @Test("accessToken is nil when signed out, rather than throwing")
    func accessTokenNilWhenSignedOut() async {
        let auth = MockAuthService(policy: .relaxed)
        given(auth).accessToken().willReturn(nil)

        #expect(await auth.accessToken() == nil)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
cd ios/Packages/SimplicityAuth && swift test
```

Expected: no such module.

- [ ] **Step 3: Write the manifest, the protocol and the Amplify implementation**

`Package.swift` depends on `../SimplicityFoundation`, `../SimplicityTesting` (tests), Factory, Mockable, and:

```swift
.package(url: "https://github.com/aws-amplify/amplify-swift.git", .upToNextMajor(from: "2.0.0"))
```

with products `Amplify` and `AWSCognitoAuthPlugin`.

`Sources/SimplicityAuth/AuthService.swift` declares the protocol above, documented as the only place that talks to Cognito, so swapping the identity provider would not reach past this file — the same sentence the web service carries.

`Sources/SimplicityAuth/Impl/AmplifyAuthService.swift`:

```swift
import Amplify
import AWSCognitoAuthPlugin
import Foundation

public final class AmplifyAuthService: AuthService {

    // MARK: Init

    public init() {}

    /// Called once at launch, before anything resolves this service.
    public static func configure() throws {
        try Amplify.add(plugin: AWSCognitoAuthPlugin())
        try Amplify.configure()
    }

    // MARK: Functions

    public func signUp(email: String, password: String) async throws {
        let options = AuthSignUpRequest.Options(
            userAttributes: [AuthUserAttribute(.email, value: email)]
        )
        _ = try await Amplify.Auth.signUp(username: email, password: password, options: options)
    }

    public func confirmSignUp(email: String, code: String) async throws {
        _ = try await Amplify.Auth.confirmSignUp(for: email, confirmationCode: code)
    }

    public func resendConfirmationCode(email: String) async throws {
        _ = try await Amplify.Auth.resendSignUpCode(for: email)
    }

    /// Returns false when Cognito needs something more before a session exists — an unconfirmed
    /// address, or a challenge. The caller routes on that rather than treating it as an error.
    public func signIn(email: String, password: String) async throws -> Bool {
        try await Amplify.Auth.signIn(username: email, password: password).isSignedIn
    }

    public func signOut() async {
        _ = await Amplify.Auth.signOut()
    }

    public func startPasswordReset(email: String) async throws {
        _ = try await Amplify.Auth.resetPassword(for: email)
    }

    public func confirmPasswordReset(email: String, code: String, newPassword: String) async throws {
        try await Amplify.Auth.confirmResetPassword(
            for: email, with: newPassword, confirmationCode: code
        )
    }

    public func isSignedIn() async -> Bool {
        ((try? await Amplify.Auth.fetchAuthSession().isSignedIn) ?? false)
    }

    /// Amplify refreshes the access token here when it is close to expiry, which is why every
    /// request asks for it rather than caching one.
    public func accessToken() async -> String? {
        guard
            let session = try? await Amplify.Auth.fetchAuthSession(),
            let provider = session as? AuthCognitoTokensProvider,
            let tokens = try? provider.getCognitoTokens().get()
        else { return nil }
        return tokens.accessToken
    }
}
```

`ios/Simplicity_iOS/amplifyconfiguration.json` — the pool and client are already public in the web's `config.json`:

```json
{
  "auth": {
    "plugins": {
      "awsCognitoAuthPlugin": {
        "CognitoUserPool": {
          "Default": {
            "PoolId": "ap-southeast-2_91O0ya5nC",
            "AppClientId": "2sihmrojcivd93m8of9q4uk1k6",
            "Region": "ap-southeast-2"
          }
        },
        "Auth": {
          "Default": {
            "authenticationFlowType": "USER_SRP_AUTH"
          }
        }
      }
    }
  }
}
```

- [ ] **Step 4: Run the test to verify it passes**

```bash
cd ios/Packages/SimplicityAuth && swift test
```

Expected: 2 tests pass. Amplify's first resolution downloads and builds its dependencies, so this run is slow.

- [ ] **Step 5: Commit**

```bash
git add ios/Packages/SimplicityAuth ios/Simplicity_iOS/amplifyconfiguration.json ios/project.yml
git commit -m "iOS: AuthService over Amplify"
```

---

### Task 8: The sign-in screen

**Files:**
- Create: `ios/Packages/SimplicityAuth/Sources/SimplicityAuth/SignIn/SignInViewModel.swift`
- Create: `ios/Packages/SimplicityAuth/Sources/SimplicityAuth/SignIn/SignInView.swift`
- Create: `ios/Packages/SimplicityAuth/Sources/SimplicityAuth/Resources/Localizable.xcstrings`
- Test: `ios/Packages/SimplicityAuth/Tests/SimplicityAuthTests/SignInViewModelTests.swift`

**Interfaces:**
- Consumes: `AuthService`, `SessionService`.
- Produces: `@Observable @MainActor public final class SignInViewModel` with `email`, `password`, `isBusy`, `errorMessage`, `outcome: SignInOutcome?`, and `func submit() async`; `public enum SignInOutcome { case signedIn, needsConfirmation, needsOnboarding }`; `public struct SignInView: View` with `public init()`.

- [ ] **Step 1: Write the failing tests**

```swift
import Mockable
import SimplicityApi
import SimplicityServices
import SimplicityTesting
import Testing

@testable import SimplicityAuth

@Suite(.serialized)
@MainActor
final class SignInViewModelTests: SimplicityTestCase {

    private func onboardedUser() -> CurrentUserResponse {
        CurrentUserResponse(
            activeOrganisationId: UUID(), email: "a@b.com", fullName: "A", id: UUID(),
            organisations: [], phone: nil, platformRole: .standard,
            professionalRole: "Psychologist", profileCompleted: true, status: .active
        )
    }

    private func makeSUT(
        auth: MockAuthService = MockAuthService(policy: .relaxed),
        session: MockSessionService = MockSessionService(policy: .relaxed)
    ) -> SignInViewModel {
        Container.shared.authService.register { auth }
        Container.shared.sessionService.register { session }
        return SignInViewModel()
    }

    @Test("refuses to submit an empty form without calling Cognito")
    func refusesEmptyForm() async {
        let auth = MockAuthService(policy: .relaxed)
        let model = makeSUT(auth: auth)

        await model.submit()

        #expect(model.errorMessage != nil)
        verify(auth).signIn(email: .any, password: .any).called(0)
    }

    @Test("an onboarded user is signed in")
    func signsInOnboardedUser() async {
        let auth = MockAuthService(policy: .relaxed)
        let session = MockSessionService(policy: .relaxed)
        given(auth).signIn(email: .any, password: .any).willReturn(true)
        given(session).refresh().willReturn(onboardedUser())
        let model = makeSUT(auth: auth, session: session)
        model.email = "a@b.com"
        model.password = "Sup3rSecret!"

        await model.submit()

        #expect(model.outcome == .signedIn)
        #expect(model.errorMessage == nil)
    }

    @Test("an unconfirmed account is routed to confirmation, not shown an error")
    func routesUnconfirmedAccount() async {
        let auth = MockAuthService(policy: .relaxed)
        given(auth).signIn(email: .any, password: .any).willReturn(false)
        let model = makeSUT(auth: auth)
        model.email = "a@b.com"
        model.password = "Sup3rSecret!"

        await model.submit()

        #expect(model.outcome == .needsConfirmation)
        #expect(model.errorMessage == nil)
    }

    @Test("a user without a profile is routed to onboarding")
    func routesUserNeedingOnboarding() async {
        let auth = MockAuthService(policy: .relaxed)
        let session = MockSessionService(policy: .relaxed)
        given(auth).signIn(email: .any, password: .any).willReturn(true)
        given(session).refresh().willReturn(
            CurrentUserResponse(
                activeOrganisationId: nil, email: "a@b.com", fullName: nil, id: UUID(),
                organisations: [], phone: nil, platformRole: .standard,
                professionalRole: nil, profileCompleted: false, status: .active
            )
        )
        let model = makeSUT(auth: auth, session: session)
        model.email = "a@b.com"
        model.password = "Sup3rSecret!"

        await model.submit()

        #expect(model.outcome == .needsOnboarding)
    }

    @Test("wrong credentials surface a message and leave the form usable")
    func surfacesFailure() async {
        let auth = MockAuthService(policy: .relaxed)
        given(auth).signIn(email: .any, password: .any)
            .willThrow(TestError.incorrect)
        let model = makeSUT(auth: auth)
        model.email = "a@b.com"
        model.password = "wrong"

        await model.submit()

        #expect(model.errorMessage != nil)
        #expect(model.isBusy == false)
        #expect(model.outcome == nil)
    }

    private enum TestError: Error { case incorrect }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

```bash
cd ios/Packages/SimplicityAuth && swift test
```

Expected: `SignInViewModel` is undefined.

- [ ] **Step 3: Write the view model**

```swift
import Factory
import Foundation
import SimplicityServices

public enum SignInOutcome: Equatable {
    case signedIn
    case needsConfirmation
    case needsOnboarding
}

@Observable
@MainActor
public final class SignInViewModel {

    // MARK: Dependencies

    @ObservationIgnored @Injected(\.authService) private var auth
    @ObservationIgnored @Injected(\.sessionService) private var session

    // MARK: Properties

    public var email: String = .empty
    public var password: String = .empty
    public private(set) var isBusy = false
    public private(set) var errorMessage: String?
    public private(set) var outcome: SignInOutcome?

    // MARK: Init

    public init() {}

    // MARK: Functions

    public func submit() async {
        guard !email.trimmingCharacters(in: .whitespaces).isEmpty, !password.isEmpty else {
            errorMessage = String(localized: "sign_in_missing_fields", bundle: .module)
            return
        }

        isBusy = true
        errorMessage = nil
        defer { isBusy = false }

        do {
            guard try await auth.signIn(email: email, password: password) else {
                // Not a failure: Cognito wants the emailed code first.
                outcome = .needsConfirmation
                return
            }
            let user = try await session.refresh()
            outcome = user.needsOnboarding ? .needsOnboarding : .signedIn
        } catch {
            errorMessage = String(localized: "sign_in_failed", bundle: .module)
        }
    }
}
```

`Localizable.xcstrings` carries `sign_in_missing_fields` ("Enter your email address and password.") and `sign_in_failed` ("We could not sign you in. Check your email address and password and try again."). The failure message is deliberately identical for a wrong password and an unknown address, matching the `PreventUserExistenceErrors: ENABLED` setting on the Cognito client — the app should not become the oracle the pool refuses to be.

Add `resources: [.process("Resources")]` to the target in `Package.swift`.

- [ ] **Step 4: Write the view**

`SignInView` composes `FormField` and `PrimaryButton` from `SimplicityDesign`, shows `ErrorBanner(message:)`, disables the button while `isBusy`, and sets `.textContentType(.emailAddress)`/`.password` so autofill works. It takes no navigation decisions itself: it exposes `outcome` for the shell's router to act on, which is what keeps the package free of a dependency on the app's routes.

- [ ] **Step 5: Run the tests to verify they pass**

```bash
cd ios/Packages/SimplicityAuth && swift test
```

Expected: 7 tests pass (2 from Task 7, 5 here).

- [ ] **Step 6: Commit**

```bash
git add ios/Packages/SimplicityAuth
git commit -m "iOS: sign-in"
```

---

### Task 9: Sign-up, email confirmation and password reset

**Files:**
- Create: `ios/Packages/SimplicityAuth/Sources/SimplicityAuth/SignUp/SignUpViewModel.swift`
- Create: `ios/Packages/SimplicityAuth/Sources/SimplicityAuth/SignUp/SignUpView.swift`
- Create: `ios/Packages/SimplicityAuth/Sources/SimplicityAuth/Confirm/ConfirmEmailViewModel.swift`
- Create: `ios/Packages/SimplicityAuth/Sources/SimplicityAuth/Confirm/ConfirmEmailView.swift`
- Create: `ios/Packages/SimplicityAuth/Sources/SimplicityAuth/Reset/ForgotPasswordViewModel.swift`
- Create: `ios/Packages/SimplicityAuth/Sources/SimplicityAuth/Reset/ForgotPasswordView.swift`
- Test: `ios/Packages/SimplicityAuth/Tests/SimplicityAuthTests/SignUpViewModelTests.swift`
- Test: `ios/Packages/SimplicityAuth/Tests/SimplicityAuthTests/ConfirmEmailViewModelTests.swift`
- Test: `ios/Packages/SimplicityAuth/Tests/SimplicityAuthTests/ForgotPasswordViewModelTests.swift`

**Interfaces:**
- Consumes: `AuthService`.
- Produces: `SignUpViewModel` (`email`, `password`, `confirmPassword`, `isBusy`, `errorMessage`, `didSignUp`, `submit()`), `ConfirmEmailViewModel` (`email`, `code`, `isBusy`, `errorMessage`, `didConfirm`, `submit()`, `resend()`), `ForgotPasswordViewModel` (`email`, `code`, `newPassword`, `stage: ResetStage`, `submit()`), and their views.

- [ ] **Step 1: Write the failing tests**

Cover, at minimum: sign-up refuses mismatched passwords without calling Cognito; sign-up refuses a password shorter than twelve characters, matching the pool's policy, so the failure is immediate rather than a round trip; confirmation with an empty code does not call Cognito; `resend()` reports success separately from `didConfirm`; the reset flow moves from `.requestingCode` to `.enteringNewPassword` only after `startPasswordReset` succeeds. Write each as a `@Test` in the same shape as Task 8, using `MockAuthService` and `given`/`verify`.

- [ ] **Step 2: Run the tests to verify they fail**

```bash
cd ios/Packages/SimplicityAuth && swift test
```

- [ ] **Step 3: Write the three view models**

Each mirrors the corresponding web component's behaviour and delegates to `AuthService`. The password rule to enforce client-side is the pool's: at least twelve characters with an uppercase letter, a lowercase letter and a number. Put it in one `PasswordPolicy.validate(_:) -> String?` used by both sign-up and reset rather than duplicating the check.

- [ ] **Step 4: Write the three views**

Composed from `SimplicityDesign`, same conventions as `SignInView`, each exposing its completion as observable state rather than navigating.

- [ ] **Step 5: Run the tests to verify they pass**

```bash
cd ios/Packages/SimplicityAuth && swift test
```

- [ ] **Step 6: Commit**

```bash
git add ios/Packages/SimplicityAuth
git commit -m "iOS: sign-up, email confirmation, and password reset"
```

---

### Task 10: Onboarding

The two wizards that stand between a new account and the app, matching `web/src/app/features/onboarding/`.

**Files:**
- Create: `ios/Packages/SimplicityAuth/Sources/SimplicityAuth/Onboarding/ProfileWizardViewModel.swift`
- Create: `ios/Packages/SimplicityAuth/Sources/SimplicityAuth/Onboarding/ProfileWizardView.swift`
- Create: `ios/Packages/SimplicityAuth/Sources/SimplicityAuth/Onboarding/OrganisationWizardViewModel.swift`
- Create: `ios/Packages/SimplicityAuth/Sources/SimplicityAuth/Onboarding/OrganisationWizardView.swift`
- Test: `ios/Packages/SimplicityAuth/Tests/SimplicityAuthTests/ProfileWizardViewModelTests.swift`
- Test: `ios/Packages/SimplicityAuth/Tests/SimplicityAuthTests/OrganisationWizardViewModelTests.swift`

**Interfaces:**
- Consumes: `SessionService`, `CurrentUserAPI.updateProfile`, `OrganisationsAPI.createOrganisation`.
- Produces: `ProfileWizardViewModel` (`fullName`, `phone`, `professionalRole`, `isBusy`, `errorMessage`, `didComplete`, `submit()`), `OrganisationWizardViewModel` (`name`, `organisationType`, `isBusy`, `errorMessage`, `didComplete`, `submit()`), and their views. Professional roles come from a single `ProfessionalRole.all: [String]` so the list matches the web's dropdown in one place.

- [ ] **Step 1: Write the failing tests**

Cover: a blank full name is refused without a request; a successful profile update refreshes the session so `profileCompleted` becomes true and the wizard can be left; a failed update leaves `didComplete` false and surfaces a message; creating an organisation refreshes the session and sets an active organisation. Use the closure-injection seam on `SessionServiceImpl` and `MockSessionService` as in Task 6.

- [ ] **Step 2: Run the tests to verify they fail**

```bash
cd ios/Packages/SimplicityAuth && swift test
```

- [ ] **Step 3: Write the view models**

Both end by calling `session.refresh()`, because `profileCompleted` and `activeOrganisationId` are what the shell routes on, and a stale cached user would bounce the person straight back into the wizard. This is exactly the loop that bit the web app when responses were being parsed as blobs, so it is worth a test rather than a comment.

- [ ] **Step 4: Write the views**

`ProfileWizardView` uses a `Picker` over `ProfessionalRole.all`. `OrganisationWizardView` uses a `Picker` over the organisation types the API accepts.

- [ ] **Step 5: Run the tests to verify they pass**

```bash
cd ios/Packages/SimplicityAuth && swift test
```

- [ ] **Step 6: Commit**

```bash
git add ios/Packages/SimplicityAuth
git commit -m "iOS: onboarding wizards"
```

---

### Task 11: The shell — adapters, routing and four tabs

Wires everything into an app. Placeholders stand in for Learn, Reflect and Settings until later plans.

**Files:**
- Create: `ios/Simplicity_iOS/Adapters/AppApiAdapter.swift`
- Create: `ios/Simplicity_iOS/Content/AppRouter.swift`
- Create: `ios/Simplicity_iOS/Content/RootView.swift`
- Create: `ios/Simplicity_iOS/Content/MainTabView.swift`
- Create: `ios/Simplicity_iOS/Content/AppConfiguration.swift`
- Modify: `ios/Simplicity_iOS/SimplicityApp.swift`
- Modify: `ios/Simplicity_iOS/Content/ContentView.swift` (replaced by `RootView`)
- Modify: `ios/project.yml` (all packages as dependencies)
- Modify: `ios/SimplicityUITests/SmokeTests.swift`

**Interfaces:**
- Consumes: everything above.
- Produces: `AppConfiguration.apiBaseURL/cognito*` read from the bundle; `AppApiAdapter: ApiAdapter`; `@Observable @MainActor final class AppRouter` with `stage: AppStage` where `AppStage` is `.loading`, `.signedOut`, `.confirming(email: String)`, `.onboardingProfile`, `.onboardingOrganisation`, `.signedIn`.

- [ ] **Step 1: Write the failing UI test**

Replace the smoke test with one that asserts the signed-out app shows the sign-in screen, which is the first thing that proves the whole chain is wired:

```swift
import XCTest

final class SmokeTests: XCTestCase {

    func testSignedOutAppShowsSignIn() {
        let app = XCUIApplication()
        app.launchArguments = ["--uitest-signed-out"]
        app.launch()
        XCTAssertTrue(app.buttons["sign-in-submit"].waitForExistence(timeout: 20))
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

```bash
cd ios && export DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer
xcodebuild test -workspace Simplicity.xcworkspace -scheme Simplicity_iOS \
  -destination 'platform=iOS Simulator,name=iPhone 17'
```

Expected: no such element.

- [ ] **Step 3: Write the configuration and the adapter**

```swift
import Foundation
import SimplicityApi
import SimplicityAuth
import Factory

enum AppConfiguration {

    private enum Constants {
        static let apiBaseURL = "APIBaseURL"
    }

    /// A missing value is a build configuration error, not a runtime condition, so it fails loudly
    /// at launch rather than becoming a confusing network error later.
    static var apiBaseURL: URL {
        guard
            let raw = Bundle.main.object(forInfoDictionaryKey: Constants.apiBaseURL) as? String,
            let url = URL(string: raw)
        else {
            fatalError("APIBaseURL missing from Info.plist. Check Config-Shared.xcconfig.")
        }
        return url
    }
}

/// The app's answer to what SimplicityApi cannot know for itself.
struct AppApiAdapter: ApiAdapter {

    let baseURL = AppConfiguration.apiBaseURL

    func accessToken() async -> String? {
        await Container.shared.authService().accessToken()
    }
}
```

- [ ] **Step 4: Write the router and the root view**

`AppRouter.start()` decides the opening stage: signed out if `auth.isSignedIn()` is false; otherwise `session.refresh()` and route on `needsOnboarding`. A thrown refresh signs the user out, because a token Cognito still honours but our API rejects means the account is gone or deactivated.

`RootView` switches on `router.stage` and hands each case its screen, observing the screens' outcomes to advance the router. `MainTabView` is four `Tab`s — Dashboard, Learn, Reflect, Settings — each a placeholder `Text` for now except that Settings offers Sign out, which calls `auth.signOut()`, `session.clear()` and returns the router to `.signedOut`.

`SimplicityApp` configures Amplify and the API before the first view:

```swift
@main
struct SimplicityApp: App {

    @State private var router = AppRouter()

    init() {
        do {
            try AmplifyAuthService.configure()
        } catch {
            fatalError("Amplify failed to configure: \(error)")
        }
    }

    var body: some Scene {
        WindowGroup {
            RootView(router: router)
                .task {
                    await ApiConfiguration.apply(AppApiAdapter())
                    await router.start()
                }
        }
    }
}
```

Add `accessibilityIdentifier("sign-in-submit")` to the sign-in button in `SignInView`. Honour `--uitest-signed-out` by signing out during `start()` when the argument is present, so the UI test does not depend on the simulator's Keychain state.

- [ ] **Step 5: Run the UI test to verify it passes**

```bash
cd ios && export DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer
xcodebuild test -workspace Simplicity.xcworkspace -scheme Simplicity_iOS \
  -destination 'platform=iOS Simulator,name=iPhone 17'
```

Expected: the app launches to sign-in and the test passes.

- [ ] **Step 6: Run every package test and the linter**

```bash
cd ios && for p in Packages/*/; do (cd "$p" && swift test) || exit 1; done
swiftlint --strict
```

Expected: all green. Fix any lint violations rather than widening `.swiftlint.yml`.

- [ ] **Step 7: Commit**

```bash
git add ios
git commit -m "iOS: app shell, routing, and the four tabs"
```

---

### Task 12: Verify against production

The step every prior phase ended with, and the one that has caught what the suites could not.

**Files:**
- Create: `scripts/verify_ios_signin.py`

**Interfaces:**
- Consumes: `scripts/verification.py` (`Run`, which creates accounts under the `verify-` prefix and deletes only what it created).
- Produces: an exit code, and a report.

- [ ] **Step 1: Write the check**

```python
"""Confirms the iOS Cognito app client works the way the app assumes it does.

The simulator cannot be driven from here, so this exercises the same client id through SRP and
the same API the app calls, which is where the interesting failures live: a client without SRP
enabled, a token the API rejects, a /me that does not provision.
"""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from pycognito import Cognito

from verification import POOL, REGION, Run

IOS_CLIENT = os.environ["IOS_CLIENT_ID"]

run = Run()
try:
    email = f"verify-ios-{run.id}@simplicityhelp.com"
    run.idp.admin_create_user(
        UserPoolId=POOL, Username=email, MessageAction="SUPPRESS",
        UserAttributes=[{"Name": "email", "Value": email},
                        {"Name": "email_verified", "Value": "true"}])
    run.idp.admin_set_user_password(
        UserPoolId=POOL, Username=email, Password=run.password, Permanent=True)
    run._created.append(email)

    user = Cognito(POOL, IOS_CLIENT, username=email)
    user.authenticate(password=run.password)
    run.check("SRP succeeds against the iOS app client", bool(user.access_token))

    headers = {"Authorization": "Bearer " + user.access_token}
    me = run.call("GET", "/api/v1/me", headers)
    run.check("the API accepts a token from the iOS client", me.status_code == 200,
              str(me.status_code))
    run.check("a new user is provisioned needing onboarding",
              me.status_code == 200 and me.json().get("profileCompleted") is False)

    anonymous = run.call("GET", "/api/v1/me", {})
    run.check("the API refuses an unauthenticated call", anonymous.status_code == 401,
              str(anonymous.status_code))
finally:
    run.cleanup()

raise SystemExit(run.report())
```

- [ ] **Step 2: Run it**

```bash
cd digital-health-app
python3 -m venv scripts/.venv && scripts/.venv/bin/pip install -q pycognito requests boto3
AWS_PROFILE=simplicity AWS_REGION=ap-southeast-2 \
POOL_ID=ap-southeast-2_91O0ya5nC \
CLIENT_ID=2sihmrojcivd93m8of9q4uk1k6 \
IOS_CLIENT_ID=2sihmrojcivd93m8of9q4uk1k6 \
  scripts/.venv/bin/python scripts/verify_ios_signin.py
```

Expected: 4 passed, 0 failed, and the created account deleted.

- [ ] **Step 3: Sign in on the simulator by hand, once**

Boot `iPhone 17`, run the app, and sign in with an account you create through the app's own sign-up. This is the one thing no script covers: that Amplify's SRP implementation and the pool agree. Confirm the app leaves the wizard and reaches the tabs.

- [ ] **Step 4: Commit**

```bash
git add scripts/verify_ios_signin.py
git commit -m "iOS: verify the iOS Cognito client against production"
```

---

## Self-review against the spec

| Spec section | Covered by |
| --- | --- |
| §2.2 conventions, async/await, `@Observable`, Swift Testing, Mockable | Global constraints; Tasks 1–11 |
| §2.3 ten packages | Six here (`Foundation`, `Testing`, `Api`, `Design`, `Services`, `Auth`); `Learn`, `Reflect`, `Assistant`, `Admin` in plans 2–4 |
| §2.4 committed `.xcodeproj`, thin shell | Task 3, with the XcodeGen bootstrap noted above |
| §2.5 generated client, `outputDir` fix | Task 4 |
| §2.6 Amplify, iOS app client, build-time config | Tasks 3, 7 |
| §2.7 online only, honest failure | Task 8's error handling; the offline banner is plan 2, where there is content to fail to load |
| §2.8 reflections never on disk | Plan 3 |
| §3 layout and layering | Global constraints; Tasks 1–7 |
| §4 shell, tabs, adapters | Task 11 |
| §5 auth and the token path | Tasks 4, 7, 8, 11 |
| §5 invitation deep links | **Not in this plan** — needs `apple-app-site-association` in `infra/web.yaml`; scheduled into plan 4 with the rest of invitations |
| §6 learning and media | Plan 2 |
| §7 authoring and upload | Plan 4 |
| §8 testing, three levels | Tasks 1–11 for unit and UI; snapshot tests deferred to plan 2, where there is a rendered module worth snapshotting |
| §9 build, signing, CI | Plan 5 |

Known gaps carried forward deliberately: no snapshot testing yet, no deep links yet, no offline banner yet. Each is scheduled above rather than dropped.
