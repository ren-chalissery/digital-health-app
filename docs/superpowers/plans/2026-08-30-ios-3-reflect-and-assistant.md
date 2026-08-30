# iOS Plan 3 — Reflect and Assistant — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A clinician keeps a private journal on their phone, and can ask questions about their training and get an answer grounded in it.

**Architecture:** Two new packages, `SimplicityReflect` and `SimplicityAssistant`, over two new services. Reflections are user-scoped and never touch disk on the device; the assistant is organisation-scoped and reads only published training.

**Tech Stack:** As Plans 1 and 2.

**Spec:** [docs/superpowers/specs/2026-08-29-phase5-ios-design.md](../specs/2026-08-29-phase5-ios-design.md)
**Follows:** [iOS Plan 2](2026-08-30-ios-2-learn.md)

## Global Constraints

Plans 1 and 2's Global Constraints still apply and are not repeated. In addition:

- `SimplicityReflect` and `SimplicityAssistant` depend on `Services`, `Design`, `Foundation`, `Api`. They must not depend on each other, on `SimplicityLearn`, or on `SimplicityAuth`.
- **No reflection text is ever written to disk on the device.** No drafts, no caches, no `@UserDefault`, no file. It lives in a view model and goes to the server.
- **Nothing connects reflections to the assistant.** Not a shared service, not a shared type, not a convenience that reads one from the other. Phase 4's spec is explicit that the feature somebody will eventually ask for — *help me reflect on this* — is precisely the one that breaks the promise.
- Localised copy goes in `en.lproj/Localizable.strings`, never `.xcstrings`. See Plan 2's commit for why.
- Suites resolving from the Factory container extend `SimplicityTestCase` and are `@Suite(.serialized)`.

## What the spec promises, and what that costs on a phone

Phase 3 promised that nobody reads a clinician's journal, including the server. Phase 4 promised the assistant never sees it. Spec §2.8 adds that a phone introduces two ways to break those promises that a browser does not have.

**Backup.** Anything written to disk is a candidate for an iCloud backup, and a journal entry in a backup is a copy of clinical reflection outside the database it was promised to stay in. So nothing is written: the editor holds text in memory and sends it. A clinician who force-quits mid-entry loses it, which is a real cost and the right trade.

**The keyboard.** iOS learns from what people type and shares that dictionary between apps. Disabling autocorrection on the editor is what stops a client's name being learned. This is a mitigation and not a guarantee, and the spec says so: short of marking the field as secure entry, which would make a journal unusable, iOS offers no way to opt out of keyboard learning entirely.

A third the spec does not mention, and should have: **the app switcher**. iOS photographs the screen when an app backgrounds, and that snapshot persists. A journal left open becomes a thumbnail of somebody's clinical reflection, visible to anyone who double-taps the home indicator. Task 5 covers the screen when the app resigns active — the standard fix, and one the web has no equivalent problem to solve.

---

### Task 1: Identifier warnings

A direct port of `web/src/app/features/reflect/identifiers.ts`, including its reasoning: this warns, it never blocks. A filter that refuses to save teaches evasion — refused a name and an NHI number, somebody writes "J.S., DOB 12/3" instead, which is still identifying, is no longer detectable, and now carries the false assurance that the field was checked.

**Files:**
- Create: `ios/Packages/SimplicityReflect/Package.swift`
- Create: `ios/Packages/SimplicityReflect/Sources/SimplicityReflect/Identifiers.swift`
- Test: `ios/Packages/SimplicityReflect/Tests/SimplicityReflectTests/IdentifiersTests.swift`

**Interfaces:**
- Consumes: nothing local.
- Produces:
  - `public struct IdentifierWarning: Equatable, Sendable { public let kind: String; public let explanation: String }`
  - `public enum Identifiers { public static func find(in text: String) -> [IdentifierWarning] }`

`Package.swift` mirrors `SimplicityLearn`'s.

- [ ] **Step 1: Write the failing tests**

Port `identifiers.spec.ts` case for case, and keep the negative cases — they are the ones that stop the warning becoming noise nobody reads.

```swift
import Testing

@testable import SimplicityReflect

@Suite("Identifiers")
struct IdentifiersTests {

    private func kinds(_ text: String) -> [String] {
        Identifiers.find(in: text).map(\.kind)
    }

    // MARK: Finds

    @Test("spots an NHI number")
    func findsNHI() {
        #expect(kinds("Discussed with ZZZ0016 today").contains("an NHI number"))
    }

    @Test("spots a Medicare number, grouped or not")
    func findsMedicare() {
        #expect(kinds("2123 45670 1").contains("a Medicare number"))
        #expect(kinds("21234567 01").contains("a Medicare number"))
    }

    @Test("spots a date of birth")
    func findsDateOfBirth() {
        #expect(kinds("born 12/03/1984").contains("a date of birth"))
        #expect(kinds("dob 1.3.1984").contains("a date of birth"))
    }

    @Test("spots an email address")
    func findsEmail() {
        #expect(kinds("wrote to ana@example.com").contains("an email address"))
    }

    @Test("spots a phone number")
    func findsPhone() {
        #expect(kinds("rang 021 555 1234").contains("a phone number"))
    }

    @Test("reports every kind it finds, not just the first")
    func findsSeveral() {
        let found = kinds("ana@example.com, born 12/03/1984")
        #expect(found.contains("an email address"))
        #expect(found.contains("a date of birth"))
    }

    @Test("each warning explains why it is asking")
    func warningsExplainThemselves() {
        let warnings = Identifiers.find(in: "ZZZ0016")
        #expect(warnings.first?.explanation.isEmpty == false)
    }

    // MARK: Leaves alone

    @Test("ordinary reflection is not flagged")
    func leavesProseAlone() {
        #expect(Identifiers.find(in: "The session went well and I felt more confident.").isEmpty)
    }

    @Test("a time of day is not a phone number")
    func timeIsNotAPhoneNumber() {
        #expect(kinds("we met at 14:30").contains("a phone number") == false)
    }

    @Test("a year on its own is not a date of birth")
    func yearIsNotADateOfBirth() {
        #expect(kinds("the 2019 guidance").contains("a date of birth") == false)
    }

    @Test("an ordinary capitalised word is not an NHI number")
    func wordIsNotAnNHI() {
        #expect(kinds("ABC then some text").contains("an NHI number") == false)
    }

    @Test("empty text produces no warnings")
    func emptyText() {
        #expect(Identifiers.find(in: "").isEmpty)
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

```bash
cd ios/Packages/SimplicityReflect && swift test
```

- [ ] **Step 3: Write the rules**

Use `NSRegularExpression`, with the same five patterns and the same wording for `kind` and `explanation`. Port the comments too — "Word boundaries both sides, so ordinary words and abbreviations are not caught", and "A time such as 14:30 has too few [digits]" — because they record why a pattern is shaped the way it is.

The file's header comment must carry the web's warning that this is **not a security control**: it runs on the device, and the server neither inspects reflections nor records anything about them, because scanning a private journal would undercut the promise that only its author reads it.

Note while porting: JavaScript's `\b` and ICU's `\b` agree for these patterns, but ICU's `\d` matches non-ASCII digits by default. Anchor the digit classes to `[0-9]` so an Arabic-Indic numeral does not slip past a rule the web would have caught.

- [ ] **Step 4: Run the tests to verify they pass**

- [ ] **Step 5: Commit**

```bash
git add ios/Packages/SimplicityReflect
git commit -m "iOS: warn about identifiers in a reflection, never block"
```

---

### Task 2: ReflectionService

**Files:**
- Create: `ios/Packages/SimplicityServices/Sources/SimplicityServices/ReflectionService.swift`
- Create: `ios/Packages/SimplicityServices/Sources/SimplicityServices/Impl/ReflectionServiceImpl.swift`
- Modify: `ios/Packages/SimplicityServices/Sources/SimplicityServices/_Package/Container+Services.swift`
- Test: `ios/Packages/SimplicityServices/Tests/SimplicityServicesTests/ReflectionServiceTests.swift`

**Interfaces:**
- Consumes: `ReflectionsAPI` — `listReflections(q:)`, `writeReflection(writeReflectionRequest:)`, `editReflection(reflectionId:writeReflectionRequest:)`, `deleteReflection(reflectionId:)`.
- Produces:
  - `@Mockable public protocol ReflectionService: AnyObject, Sendable` with `list(query: String?) async throws -> [ReflectionResponse]`, `write(title: String, body: String) async throws -> ReflectionResponse`, `edit(id: UUID, title: String, body: String) async throws -> ReflectionResponse`, `delete(id: UUID) async throws`
  - `Container.shared.reflectionService: Factory<ReflectionService>` scoped `.singleton`

Closure injection per call, as `LearningServiceImpl` does, for the same reason.

Note the shape of these endpoints: they are `/api/v1/me/reflections`, with **no organisation in the path**. A reflection belongs to a person, not to a workplace, and the service must not acquire an `orgId` parameter for symmetry with the others.

- [ ] **Step 1: Write the failing tests**

Cover: `list` passes a nil query through as nil rather than an empty string, since the server treats them differently; `list` passes a query through unchanged; `write` sends the title and body it was given; `edit` sends the id it was given; a throwing call propagates.

- [ ] **Step 2: Run the tests to verify they fail**

- [ ] **Step 3: Write the protocol, implementation and registration**

- [ ] **Step 4: Run the tests to verify they pass**

- [ ] **Step 5: Commit**

```bash
git add ios/Packages/SimplicityServices
git commit -m "iOS: ReflectionService"
```

---

### Task 3: The journal

**Files:**
- Create: `ios/Packages/SimplicityReflect/Sources/SimplicityReflect/ReflectViewModel.swift`
- Create: `ios/Packages/SimplicityReflect/Sources/SimplicityReflect/ReflectView.swift`
- Create: `ios/Packages/SimplicityReflect/Sources/SimplicityReflect/ReflectionEditor.swift`
- Create: `ios/Packages/SimplicityReflect/Sources/SimplicityReflect/Resources/en.lproj/Localizable.strings`
- Modify: `ios/Packages/SimplicityReflect/Package.swift` (add resources and the service dependencies)
- Test: `ios/Packages/SimplicityReflect/Tests/SimplicityReflectTests/ReflectViewModelTests.swift`

**Interfaces:**
- Consumes: `ReflectionService`, `Identifiers`.
- Produces: `@Observable @MainActor public final class ReflectViewModel` with `isLoading`, `isSaving`, `errorMessage`, `entries: [ReflectionResponse]`, `query: String`, `title: String`, `body: String`, `editingId: UUID?`, `warnings: [IdentifierWarning]`, `var canSave: Bool`, `func load() async`, `func search() async`, `func save() async`, `func edit(_ entry: ReflectionResponse)`, `func clear()`, `func delete(_ entry: ReflectionResponse) async`; and `public struct ReflectView: View { public init() }`.

- [ ] **Step 1: Write the failing tests**

Cover:

- `warnings` recompute as the body changes, not only on save — the point is to change what gets written, which is too late once it is written.
- `warnings` consider the title as well as the body. A name in a title is still a name.
- `canSave` is false with an empty body, and true with one. An empty entry is not a reflection.
- Saving a new entry calls `write`, not `edit`.
- Saving while `editingId` is set calls `edit` with that id.
- A successful save clears the fields, so the next entry does not start with the last one's text.
- **A failed save does not clear the fields.** This is the important one: the text exists nowhere else — not on disk, not in a draft — so clearing it on failure destroys what the clinician wrote.
- `edit` populates the fields from the entry and sets `editingId`.
- `clear` resets the fields and `editingId`.
- `search` passes the query to the service; an empty query is sent as nil.
- Deleting removes the entry from the list.
- A failed load sets a message and does not leave an empty list looking like an empty journal.

- [ ] **Step 2: Run the tests to verify they fail**

- [ ] **Step 3: Write the view model**

`warnings` is a computed property over `title + " " + body`, so it recomputes with every keystroke through `@Observable`.

- [ ] **Step 4: Write the views**

`ReflectionEditor` is a `TextField` for the title and a `TextEditor` for the body, with `.autocorrectionDisabled()` on both — the mitigation from spec §2.8 against the shared keyboard dictionary. Below them, one line per warning: what it saw and why the product asks, in `brandTextSecondary` rather than red, because this is not an error.

`ReflectView` lists entries newest first, showing title, the first line or so of the body, and the date. `.searchable` drives `search()`. Swipe to delete, with a confirmation — an accidental swipe destroying a private reflection is unrecoverable.

Both views must hold no state of their own beyond what the view model owns, so that nothing survives the view being torn down.

- [ ] **Step 5: Run the tests to verify they pass**

- [ ] **Step 6: Commit**

```bash
git add ios/Packages/SimplicityReflect
git commit -m "iOS: the Reflect journal"
```

---

### Task 4: AssistantService and the ask sheet

**Files:**
- Create: `ios/Packages/SimplicityServices/Sources/SimplicityServices/AssistantService.swift`
- Create: `ios/Packages/SimplicityServices/Sources/SimplicityServices/Impl/AssistantServiceImpl.swift`
- Modify: `ios/Packages/SimplicityServices/Sources/SimplicityServices/_Package/Container+Services.swift`
- Create: `ios/Packages/SimplicityAssistant/Package.swift`
- Create: `ios/Packages/SimplicityAssistant/Sources/SimplicityAssistant/AskViewModel.swift`
- Create: `ios/Packages/SimplicityAssistant/Sources/SimplicityAssistant/AskView.swift`
- Create: `ios/Packages/SimplicityAssistant/Sources/SimplicityAssistant/Resources/en.lproj/Localizable.strings`
- Test: `ios/Packages/SimplicityServices/Tests/SimplicityServicesTests/AssistantServiceTests.swift`
- Test: `ios/Packages/SimplicityAssistant/Tests/SimplicityAssistantTests/AskViewModelTests.swift`

**Interfaces:**
- Consumes: `AssistantAPI.askAssistant(orgId:askRequest:)`, `SessionService`.
- Produces:
  - `@Mockable public protocol AssistantService: AnyObject, Sendable { func ask(orgId: UUID, question: String) async throws -> AnswerResponse }`
  - `Container.shared.assistantService: Factory<AssistantService>` scoped `.singleton`
  - `@Observable @MainActor public final class AskViewModel` with `question: String`, `isAsking`, `errorMessage`, `answer: AnswerResponse?`, `var canAsk: Bool`, `func ask() async`, `func reset()`
  - `public struct AskView: View { public init(onOpenModule: @escaping (UUID) -> Void) }`

- [ ] **Step 1: Write the failing tests**

Cover, in the view model:

- `canAsk` is false for an empty or whitespace question, and no request is made.
- A successful answer is exposed with its citations.
- **An unanswered response is not an error.** `answered == false` is the assistant working correctly — it means the training does not cover the question — and it must not be rendered as a failure. This is the test that protects the whole design of Phase 4.
- An unanswered response shows no citations, even if the payload contained any.
- A thrown error sets `errorMessage` and leaves `answer` nil, which *is* a failure and reads differently.
- Asking again replaces the previous answer rather than appending; this is single-turn, with no history.
- `reset` clears the question and the answer.
- Asking with no active organisation makes no request.

- [ ] **Step 2: Run the tests to verify they fail**

- [ ] **Step 3: Write the service and view model**

- [ ] **Step 4: Write the view**

Framing is the whole point here, and Phase 4's spec is prescriptive about it. The sheet is titled **"Ask about the training"**, never "assistant" and never anything suggesting advice. The unanswered response says plainly that the training does not cover it and that supervision is the right route — it must not apologise or offer to try harder, because both imply the answer exists somewhere in the product.

Citations list module and section. A citation whose `assignedToYou` is true is tappable and calls `onOpenModule`; one that is false is named without a link, so a clinician is not sent somewhere they cannot go.

The question field gets `.autocorrectionDisabled()` for the same reason the journal editor does: a clinician asking about the training may still type something clinical.

- [ ] **Step 5: Run the tests to verify they pass**

- [ ] **Step 6: Commit**

```bash
git add ios/Packages/SimplicityServices ios/Packages/SimplicityAssistant ios/project.yml
git commit -m "iOS: ask about the training"
```

---

### Task 5: Wire both into the shell, and cover the app switcher

**Files:**
- Modify: `ios/Simplicity_iOS/Content/MainTabView.swift`
- Create: `ios/Simplicity_iOS/Content/PrivacyScreen.swift`
- Modify: `ios/Simplicity_iOS/Content/RootView.swift`
- Modify: `ios/project.yml`
- Modify: `ios/SimplicityUITests/SmokeTests.swift`

**Interfaces:**
- Consumes: `ReflectView`, `AskView`.
- Produces: a working Reflect tab; an "Ask about the training" button on the Learn tab presenting `AskView` as a sheet; a privacy overlay while the app is not active.

- [ ] **Step 1: Write the failing test**

Extend the signed-out smoke test to assert the sign-in screen still appears — a regression guard on the shell, since this task edits it. A UI test cannot see the app-switcher snapshot, so Task 5's privacy behaviour is verified by hand in Step 5.

- [ ] **Step 2: Run it to verify the shell still builds**

- [ ] **Step 3: Replace the Reflect placeholder and add the ask sheet**

The assistant is a sheet raised from Learn rather than a fifth tab, for the reason Phase 4 gave: a fifth tab would mean reworking the shell for a feature nobody has used yet.

- [ ] **Step 4: Write the privacy screen**

```swift
import SwiftUI

/// Hides the interface while the app is not frontmost.
///
/// iOS photographs the screen when an app backgrounds, and that snapshot persists and is shown in
/// the app switcher. A journal left open would become a thumbnail of somebody's clinical
/// reflection, visible to anyone who double-taps the home indicator. The web has no equivalent
/// exposure, which is why the spec did not anticipate this.
struct PrivacyScreen: ViewModifier {

    @Environment(\.scenePhase) private var phase

    func body(content: Content) -> some View {
        content.overlay {
            // .inactive covers the moment the snapshot is taken, which is before .background.
            if phase != .active {
                Color.brandSurface
                    .ignoresSafeArea()
                    .overlay(Image(systemName: "lock.fill").font(.largeTitle))
                    .transition(.opacity)
            }
        }
    }
}

extension View {
    func privacyScreen() -> some View { modifier(PrivacyScreen()) }
}
```

Apply it to `RootView`, not only to Reflect: the module reader and the ask sheet can both be showing content a clinician would not want on a lock screen.

- [ ] **Step 5: Verify the snapshot by hand**

Run on a simulator, open Reflect, type an entry, then press Home and open the app switcher. The card must show the lock overlay and not the text. This cannot be automated — XCUITest cannot read the app-switcher snapshot — so it is a manual check, and worth doing once.

- [ ] **Step 6: Commit**

```bash
git add ios
git commit -m "iOS: Reflect and the ask sheet in the shell"
```

---

### Task 6: Verify against production

**Files:**
- Create: `scripts/verify_ios_reflect_assistant.py`

- [ ] **Step 1: Write the check**

Using `Run` from `verification.py`:

1. Write a reflection; assert it comes back with an id and the body sent.
2. List reflections; assert it is there.
3. Search for a word in its body; assert it is found. Search for a word that is not; assert it is not.
4. Edit it; assert the body changed and the id did not.
5. **Create a second account and assert it gets 404, not 403, reading the first account's reflection by id.** Phase 3 chose 404 deliberately: 403 would confirm the entry exists, which is itself a disclosure about a private journal.
6. Assert the second account's list does not contain the first's entry.
7. Delete it; assert a subsequent read is 404.
8. Publish a module whose content is distinctive, wait for the indexer, then ask a question it answers and assert `answered` is true with a citation naming that module.
9. Ask something plainly outside the training and assert `answered` is false with no citations — the refusal path, which is the one that matters most.

Step 8 depends on the asynchronous indexer, so poll for up to two minutes and report a clear skip rather than a failure if the content is not indexed in time. A flaky assertion about a scheduled job teaches people to ignore the suite.

- [ ] **Step 2: Run it**

- [ ] **Step 3: Drive the app against that data on a simulator**

Write a reflection in the app, search for it, then ask the assistant a question the module answers and one it does not. Delete the account afterwards.

- [ ] **Step 4: Commit**

```bash
git add scripts/verify_ios_reflect_assistant.py
git commit -m "iOS: verify reflections and the assistant against production"
```

---

## Self-review against the spec

| Spec section | Covered by |
| --- | --- |
| §2.8 reflections never written to disk | Global constraints; Tasks 3 — no draft, no cache, and a test that a failed save keeps the text |
| §2.8 keyboard learning | Task 3 and Task 4 — `.autocorrectionDisabled()` on every field that could take clinical text |
| §2.8 identifier warnings ported | Task 1 |
| §2.8 app-switcher snapshot | Task 5 — **not in the spec**; added because a phone exposes what a browser does not |
| Phase 3: 404 not 403 for another user's entry | Task 6, step 5 |
| Phase 4: refusal is not an error | Task 4 — the assertion the whole design rests on |
| Phase 4: citations link only where assigned | Task 4 |
| Phase 4: framed as asking about training | Task 4 |
| Phase 4: no reflection reaches the assistant | Global constraints — enforced by package boundaries: `SimplicityAssistant` does not depend on `SimplicityReflect`, and the compiler holds that |
| §4 assistant is a sheet, not a fifth tab | Task 5 |

One gap carried deliberately: **the assistant's rate limit is not exercised**. The server allows thirty questions an hour per clinician and refuses the thirty-first. Verifying it means making thirty real Bedrock calls against production, which costs money and proves something the backend suite already covers.
