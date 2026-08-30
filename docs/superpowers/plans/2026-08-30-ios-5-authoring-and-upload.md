# iOS Plan 5 — Module authoring and video upload — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** An administrator writes a training module on their phone, films or picks a video for it, sets a quiz, assigns it to teams, and publishes — and a learner sees it.

**Architecture:** Two services (`AuthoringService`, `MediaService`) and the authoring screens added to `SimplicityAdmin`. Upload goes straight to S3 through a presigned URL on a background session; the API is only told before and after.

**Tech Stack:** As Plans 1 to 4, plus PhotosUI and a background `URLSession`.

**Spec:** [docs/superpowers/specs/2026-08-29-phase5-ios-design.md](../specs/2026-08-29-phase5-ios-design.md)
**Follows:** [iOS Plan 4](2026-08-30-ios-4-organisation-administration.md)

## Global Constraints

Plans 1 to 4's Global Constraints still apply. In addition:

- Authoring lives in `SimplicityAdmin`, behind the same `isOrgAdmin` check as the rest of it.
- **A draft is the only editable thing.** A published version is immutable; editing means opening a draft, changing it, and publishing again.
- Localised copy in `en.lproj/Localizable.strings`.
- No video file is copied into the app's Documents directory. Temporary files go in `FileManager.default.temporaryDirectory` and are removed once the upload finishes or fails.

## The upload is a single PUT, not multipart

Spec §7 said uploads use "the presigned multipart PUT the web already uses", "resumable per part". **That is not what the server offers.** `S3ObjectStore.presignPut` issues one presigned `PutObject` URL, and `POST /api/v1/orgs/{orgId}/media` returns `UploadTargetResponse { assetId, uploadUrl }` — a single URL for the whole file.

The consequence is worth stating plainly rather than discovering on a ward: **a failed upload starts again from nothing.** At the 500 MB cap that is a long way to fall, and hospital wifi is exactly where it will happen. Two things follow.

A background `URLSession` is not a nicety here, it is the mitigation that matters: an upload that survives the screen locking is the difference between finishing and restarting. `uploadTask(with:fromFile:)` continues outside the app's lifetime and reports progress, which a foreground `async` request cannot.

And the size check happens *before* the upload starts, not after. Failing a twenty-minute upload for a reason knowable in the first second is the worst version of this.

Real resumability needs S3 multipart, which is a backend change to `S3ObjectStore` and the media endpoints. Out of scope, and noted at the end.

## Three shapes worth knowing before writing code

`MediaAssetResponse.status` is a **plain `String`**, not a generated enum — the backend's states are `PENDING`, `PROCESSING`, `READY`, `FAILED`. Compare against named constants rather than scattering literals.

`VersionResponse.status` is likewise a `String`, `DRAFT` or `PUBLISHED`.

Captions go up as a `text/vtt` **request body**, not presigned — the generated `setCaptions(orgId:assetId:body:)` takes a `String`. The endpoint's own description says why: "a caption file is kilobytes where a video is hundreds of megabytes."

---

### Task 1: MediaService

**Files:**
- Create: `ios/Packages/SimplicityServices/Sources/SimplicityServices/MediaService.swift`
- Create: `ios/Packages/SimplicityServices/Sources/SimplicityServices/Impl/MediaServiceImpl.swift`
- Modify: `ios/Packages/SimplicityServices/Sources/SimplicityServices/_Package/Container+Services.swift`
- Test: `ios/Packages/SimplicityServices/Tests/SimplicityServicesTests/MediaServiceTests.swift`

**Interfaces:**
- Produces:
  - `public enum MediaStatus: String { case pending = "PENDING", processing = "PROCESSING", ready = "READY", failed = "FAILED" }`
  - `public extension MediaAssetResponse { var mediaStatus: MediaStatus?; var isReady: Bool; var isFinished: Bool }` — `isFinished` covers ready **and** failed, because both end polling
  - `@Mockable public protocol MediaService` with `list(orgId:)`, `register(orgId:filename:contentType:sizeBytes:) -> UploadTargetResponse`, `markUploaded(orgId:assetId:)`, `asset(orgId:assetId:)`, `setCaptions(orgId:assetId:vtt:)`, `removeCaptions(orgId:assetId:)`, `delete(orgId:assetId:)`
  - `Container.shared.mediaService`

- [ ] **Step 1: Write the failing tests**

Cover: `mediaStatus` maps each of the four strings and is nil for one the app does not know, so a new server state does not read as `READY`; `isFinished` is true for ready and failed and false for pending and processing; `register` sends the filename, content type and size; a throwing call propagates.

- [ ] **Step 2: Run to verify they fail** — `cd ios/Packages/SimplicityServices && swift test`

- [ ] **Step 3: Write the protocol, implementation and registration**

- [ ] **Step 4: Run to verify they pass**

- [ ] **Step 5: Commit** — `git commit -m "iOS: MediaService"`

---

### Task 2: AuthoringService

**Files:**
- Create: `ios/Packages/SimplicityServices/Sources/SimplicityServices/AuthoringService.swift`
- Create: `ios/Packages/SimplicityServices/Sources/SimplicityServices/Impl/AuthoringServiceImpl.swift`
- Modify: `_Package/Container+Services.swift`
- Test: `ios/Packages/SimplicityServices/Tests/SimplicityServicesTests/AuthoringServiceTests.swift`

**Interfaces:**
- Produces:
  - `@Mockable public protocol AuthoringService` with `modules(orgId:)`, `module(orgId:moduleId:)`, `create(orgId:title:summary:)`, `openDraft(orgId:moduleId:)`, `replaceSections(orgId:moduleId:sections: [SectionInput])`, `replaceQuiz(orgId:moduleId:questions: [QuestionInput])`, `publish(orgId:moduleId:supersedesCompletions:)`, `assignTeams(orgId:moduleId:teamIds:)`, `archive(orgId:moduleId:)`
  - `public extension AuthoredModuleResponse { var hasDraft: Bool; var isPublished: Bool }`
  - `Container.shared.authoringService`

- [ ] **Step 1: Write the failing tests**

Cover: `create` sends an empty summary as nil; `publish` sends the `supersedesCompletions` it was given, **and a test for each of true and false separately** — this flag decides whether everyone who finished the module has to do it again, and getting it backwards is the most expensive mistake available here; `assignTeams` sends an empty array when every team is deselected, rather than omitting the field, since that is how a module is unassigned; `hasDraft` is true only when `draft` is present.

- [ ] **Step 2: Run to verify they fail**

- [ ] **Step 3: Write the protocol, implementation and registration**

- [ ] **Step 4: Run to verify they pass**

- [ ] **Step 5: Commit** — `git commit -m "iOS: AuthoringService"`

---

### Task 3: The authored module list

**Files:**
- Create: `ios/Packages/SimplicityAdmin/Sources/SimplicityAdmin/Authoring/ModuleAdminViewModel.swift`
- Create: `ios/Packages/SimplicityAdmin/Sources/SimplicityAdmin/Authoring/ModuleAdminView.swift`
- Modify: `ios/Packages/SimplicityAdmin/Sources/SimplicityAdmin/Settings/SettingsView.swift` (a Modules link)
- Modify: `AdminDestination` (`case modules`, `case module(id: UUID, title: String)`)
- Test: `ios/Packages/SimplicityAdmin/Tests/SimplicityAdminTests/ModuleAdminViewModelTests.swift`

**Interfaces:**
- Produces: `ModuleAdminViewModel` with `modules`, `newTitle`, `newSummary`, `canCreate`, `load()`, `create()`, `archive(_:)`; `ModuleAdminView`.

- [ ] **Step 1: Write the failing tests**

Cover: a module needs a title; creating appends and clears; a failed creation keeps the typing; archiving removes it from the list; a failed archive keeps it listed; the list distinguishes published from draft-only, because "not published yet" and "published, with unpublished edits" look identical otherwise and mean very different things to a learner.

- [ ] **Step 2 to 5: fail, implement, pass, commit** — `git commit -m "iOS: the authored module list"`

---

### Task 4: The section editor

**Files:**
- Create: `.../Authoring/ModuleEditorViewModel.swift`
- Create: `.../Authoring/ModuleEditorView.swift`
- Create: `.../Authoring/SectionEditorView.swift`
- Test: `.../SimplicityAdminTests/ModuleEditorViewModelTests.swift`

**Interfaces:**
- Produces: `ModuleEditorViewModel` with `init(moduleId:)`, `module`, `sections: [DraftSection]`, `isDirty`, `load()`, `openDraft()`, `addSection()`, `updateSection(_:title:body:)`, `moveSection(from:to:)`, `deleteSection(_:)`, `save()`; and `public struct DraftSection: Identifiable` holding a client-side id, title, body and optional `mediaAssetId`.

`DraftSection` exists because `PUT /draft/sections` **replaces the whole list**. Editing one section means sending them all, so the editor holds the whole array and identity has to survive reordering — a server `sectionId` is absent for a section not yet saved.

- [ ] **Step 1: Write the failing tests**

Cover:

- Loading a module with a published version but no draft shows the published content, so editing starts from what is live rather than from nothing.
- `openDraft` is what makes it editable; before that, editing is refused.
- Adding a section appends an empty one and marks the editor dirty.
- Editing a section's body marks it dirty; saving clears dirty.
- Reordering changes the order sent, and a test asserts the *order of the array passed to the service*, since position is the whole meaning of a section list.
- Deleting removes it from what would be sent.
- Saving sends every section, not only the changed one — the endpoint replaces.
- A section with an empty title is refused before the request, because a section list with a blank heading is unreadable in the reader.
- A failed save leaves `isDirty` true, so nothing suggests the work was stored.

- [ ] **Step 2 to 5: fail, implement, pass, commit** — `git commit -m "iOS: the section editor"`

---

### Task 5: The quiz editor

**Files:**
- Create: `.../Authoring/QuizEditorViewModel.swift`
- Create: `.../Authoring/QuizEditorView.swift`
- Test: `.../SimplicityAdminTests/QuizEditorViewModelTests.swift`

**Interfaces:**
- Produces: `QuizEditorViewModel` with `questions: [DraftQuestion]`, `addQuestion()`, `addOption(to:)`, `setCorrect(question:option:)`, `deleteQuestion(_:)`, `deleteOption(question:option:)`, `save()`, `validationMessage: String?`; `DraftQuestion` and `DraftOption` with client-side ids.

- [ ] **Step 1: Write the failing tests**

The validation here is the point, and it mirrors what the server enforces:

- A question needs a prompt.
- A question needs at least two options — one option is not a question.
- A question needs exactly one correct option. Test both none and two, separately.
- Marking an option correct unmarks the previous one, rather than allowing two.
- `validationMessage` names the offending question by its position, because "a question is invalid" in a list of nine is useless.
- A valid quiz saves and sends every question.
- Saving an empty quiz is allowed and removes the quiz — a module may legitimately have none.

- [ ] **Step 2 to 5: fail, implement, pass, commit** — `git commit -m "iOS: the quiz editor"`

---

### Task 6: Video

**Files:**
- Create: `.../Authoring/VideoUploader.swift`
- Create: `.../Authoring/VideoUploadViewModel.swift`
- Create: `.../Authoring/VideoUploadView.swift`
- Test: `.../SimplicityAdminTests/VideoUploadViewModelTests.swift`

**Interfaces:**
- Produces:
  - `public protocol VideoUploader: Sendable { func upload(fileURL: URL, to target: URL, contentType: String, progress: @Sendable (Double) -> Void) async throws }`
  - `BackgroundVideoUploader: VideoUploader`
  - `VideoUploadViewModel` with `init(orgId:)`, `pickedItem`, `state: UploadState`, `progress: Double`, `asset: MediaAssetResponse?`, `errorMessage`, `start(fileURL:filename:sizeBytes:)`, `pollUntilFinished()`
  - `public enum UploadState { case idle, tooLarge, uploading, processing, ready, failed }`

- [ ] **Step 1: Write the failing tests**

With a fake `VideoUploader`, cover:

- A file over the 500 MB cap goes to `.tooLarge` **without registering an asset**. Failing at the end of a long upload for a reason knowable at the start is unkind, and the test asserts no request was made.
- A successful upload registers, uploads to the returned URL, then marks uploaded — in that order, asserted by a recorded sequence. Marking uploaded before the bytes arrive would start a transcode of nothing.
- A failed upload does **not** mark uploaded, so no transcode is started for a file that is not there.
- Progress is reported and reaches 1.
- Polling stops on `READY` and on `FAILED`, and a test for each — polling forever on a failed transcode is a battery drain and a lie.
- A `FAILED` asset surfaces its `failureReason` when the server gives one.

- [ ] **Step 2: Run to verify they fail**

- [ ] **Step 3: Write the uploader and view model**

`BackgroundVideoUploader` uses a background `URLSessionConfiguration` and `uploadTask(with:fromFile:)`. A background session needs a delegate rather than a completion handler, so bridge it with a continuation and keep the delegate alive for the session's lifetime.

- [ ] **Step 4: Write the view**

`PhotosPicker` filtered to `.videos`, plus the camera. The picked item is copied to `FileManager.default.temporaryDirectory` first, because a background upload needs a file URL that outlives the picker, and removed afterwards.

Show the size before uploading, progress while it runs, and the transcode state after — a video is not usable the moment the bytes arrive, and silence at that point reads as a hang.

- [ ] **Step 5: Run to verify they pass**

- [ ] **Step 6: Commit** — `git commit -m "iOS: filming and uploading a video"`

---

### Task 7: Publishing and assignment

**Files:**
- Create: `.../Authoring/PublishViewModel.swift`
- Create: `.../Authoring/PublishView.swift`
- Test: `.../SimplicityAdminTests/PublishViewModelTests.swift`

**Interfaces:**
- Produces: `PublishViewModel` with `init(moduleId:)`, `teams`, `selectedTeamIds: Set<UUID>`, `supersedesCompletions: Bool`, `load()`, `assign()`, `publish()`.

- [ ] **Step 1: Write the failing tests**

Cover: teams load with the currently assigned ones pre-selected; deselecting everything and saving sends an empty array; publishing passes `supersedesCompletions` through; publishing a module with no sections is refused before the request, since the server refuses it too and a round trip to learn that is wasted.

- [ ] **Step 2 to 5: fail, implement, pass, commit**

The `supersedesCompletions` control is the one piece of authoring UI that needs real copy rather than a label. It decides whether everybody who already completed the module has to do it again, so the switch reads **"Everyone who finished this must do it again"** with the explanation that a corrected typo should leave completions alone — the wording the API's own description uses.

`git commit -m "iOS: publishing and team assignment"`

---

### Task 8: Wire authoring into Settings

**Files:**
- Modify: `.../Settings/SettingsView.swift`, `MainTabView.swift`, `ios/project.yml`

- [ ] **Step 1 to 4**: add the Modules link and destinations, run the smoke test, commit.

---

### Task 9: Verify against production

**Files:**
- Create: `scripts/verify_ios_authoring.py`

- [ ] **Step 1: Write the check**

1. Create a module, open a draft, add two sections, publish, assign to a team.
2. Register an upload for a small real MP4, PUT the bytes to the presigned URL, mark uploaded.
3. Poll the asset until `READY` or `FAILED`, with a timeout that **skips loudly** rather than passing — transcoding is a real job and its duration is not ours to assume, but a `FAILED` is a failure, not a skip.
4. Attach the asset to a section, republish, and assert a learner sees the section with its `mediaAssetId`.
5. Set captions with a valid WebVTT body; assert `hasCaptions`.
6. Set captions with something that is not WebVTT; assert it is refused. The server validates this and the app relies on it.
7. Publish with `supersedesCompletions: true` and assert a previously completed learner is returned to `NEEDS_REDOING`. This is the assertion worth the most in the whole plan: it is the one behaviour where getting it wrong silently un-completes an entire organisation's training, or fails to.

- [ ] **Step 2: Run it**

- [ ] **Step 3: Drive the app on a simulator** — film nothing, but pick a video from the simulator's library, upload it, and watch it reach `READY`.

- [ ] **Step 4: Commit**

---

## Self-review against the spec

| Spec section | Covered by |
| --- | --- |
| §7 module and quiz editing over the draft endpoints | Tasks 4, 5 |
| §7 upload from camera or library, surviving backgrounding | Task 6 |
| §7 "presigned multipart PUT", "resumable per part" | **Corrected.** The server issues one presigned PUT; there are no parts and no resume. Recorded above |
| §7 size checked before starting | Task 6, asserted by a test that no asset is registered |
| §7 transcoding polled until READY | Task 6, stopping on FAILED too |
| §2.1 authoring is why parity was chosen | Tasks 3 to 7 |

Two gaps carried deliberately. **Resumable upload** needs S3 multipart in `S3ObjectStore` and new endpoints — a backend change, and the right one if uploads prove fragile in the field. And **caption authoring** is limited to attaching a `.vtt` a person already has; writing or timing captions in the app is a different product.
