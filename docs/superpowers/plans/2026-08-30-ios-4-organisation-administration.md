# iOS Plan 4 — Organisation administration — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** An administrator manages their organisation from a phone — members and their roles, teams and who is in them, invitations — and anyone can switch between the organisations they belong to or leave one.

**Architecture:** One new package, `SimplicityAdmin`, over an `OrganisationService`. Everything administrative is gated on the caller's role in the *active* organisation, read from the session rather than guessed.

**Tech Stack:** As Plans 1 to 3.

**Spec:** [docs/superpowers/specs/2026-08-29-phase5-ios-design.md](../specs/2026-08-29-phase5-ios-design.md)
**Follows:** [iOS Plan 3](2026-08-30-ios-3-reflect-and-assistant.md)

## Scope

Phase 5's admin surface is 29 endpoints and 1,633 lines of Angular, which is two plans, not one. This is the first: **who is in the organisation**. Module authoring, team assignment and video upload are the second, and are not attempted here.

In scope: the Settings tab proper — profile, organisation switching, leaving an organisation, members and their roles, teams and their membership, invitations.

## Global Constraints

Plans 1 to 3's Global Constraints still apply. In addition:

- `SimplicityAdmin` depends on `Services`, `Design`, `Foundation`, `Api`. Not on `Learn`, `Reflect`, `Assistant` or `Auth`.
- **Administrative capability comes from the session, never from a local guess.** `activeOrganisation()?.orgRole == .orgAdmin` is the only test, matching `web/src/app/core/session.service.ts`.
- Hiding a control is presentation, not security. The server authorises every one of these calls, and the app must handle a 403 gracefully rather than assuming it cannot happen.
- Every destructive action confirms first, and the confirmation says what will actually happen — not "Are you sure?".
- Localised copy in `en.lproj/Localizable.strings`.

## Two things worth deciding before writing code

### Switching organisations is a session change, not a navigation

`PUT /api/v1/me/active-organisation` returns a new `CurrentUserResponse`, and everything already on screen — the Dashboard's module list, Learn, the ask sheet — belongs to the organisation being left. `SessionServiceImpl.setActiveOrganisation` already replaces the cached user, so the fix is that switching must pop every navigation stack and let the tabs reload, rather than leaving a module reader open on a module the clinician may no longer be able to see.

### Leaving is not removing, and the difference matters

`DELETE /members/me` and `DELETE /members/{userId}` are different endpoints with different consequences, and an administrator sees both. Leaving is something you do to yourself and cannot undo without a fresh invitation. Removing is something you do to a colleague, and the web's confirmation names the consequence — *"they will also leave every team in this organisation"* — rather than asking a bare "are you sure". Both wordings port.

A sole administrator leaving would strand the organisation. The server decides whether to allow it; the app must surface that refusal clearly rather than failing silently, which means a 409 needs its own message.

---

### Task 1: OrganisationService

**Files:**
- Create: `ios/Packages/SimplicityServices/Sources/SimplicityServices/OrganisationService.swift`
- Create: `ios/Packages/SimplicityServices/Sources/SimplicityServices/Impl/OrganisationServiceImpl.swift`
- Modify: `ios/Packages/SimplicityServices/Sources/SimplicityServices/_Package/Container+Services.swift`
- Test: `ios/Packages/SimplicityServices/Tests/SimplicityServicesTests/OrganisationServiceTests.swift`

**Interfaces:**
- Consumes: `OrganisationsAPI`, `TeamsAPI`, `InvitationsAPI`.
- Produces:
  - `@Mockable public protocol OrganisationService: AnyObject, Sendable` with:
    - `members(orgId: UUID) async throws -> [OrgMemberResponse]`
    - `changeRole(orgId: UUID, userId: UUID, role: ChangeOrgRoleRequest.OrgRole) async throws -> OrgMemberResponse`
    - `removeMember(orgId: UUID, userId: UUID) async throws`
    - `leave(orgId: UUID) async throws`
    - `teams(orgId: UUID) async throws -> [TeamResponse]`
    - `createTeam(orgId: UUID, name: String, description: String?) async throws -> TeamResponse`
    - `deleteTeam(orgId: UUID, teamId: UUID) async throws`
    - `teamMembers(orgId: UUID, teamId: UUID) async throws -> [OrgMemberResponse]`
    - `addTeamMember(orgId: UUID, teamId: UUID, userId: UUID, role: AddTeamMemberRequest.TeamRole) async throws`
    - `removeTeamMember(orgId: UUID, teamId: UUID, userId: UUID) async throws`
    - `invitations(orgId: UUID) async throws -> [InvitationResponse]`
    - `invite(orgId: UUID, email: String, orgRole: CreateInvitationRequest.OrgRole, teamId: UUID?, teamRole: CreateInvitationRequest.TeamRole?) async throws -> InvitationResponse`
    - `revokeInvitation(orgId: UUID, invitationId: UUID) async throws`
  - `Container.shared.organisationService: Factory<OrganisationService>` scoped `.singleton`

Closure injection per call, as the other services do.

- [ ] **Step 1: Write the failing tests**

Cover: `createTeam` sends an empty description as nil rather than ""; `invite` omits `teamRole` when no team is chosen, since a team role without a team is meaningless and the server refuses it; `invite` trims and lowercases the email, because an invitation to `Ana@Example.com ` should reach the same person as `ana@example.com`; a throwing call propagates.

- [ ] **Step 2: Run the tests to verify they fail**

- [ ] **Step 3: Write the protocol, implementation and registration**

- [ ] **Step 4: Run the tests to verify they pass**

- [ ] **Step 5: Commit**

```bash
git add ios/Packages/SimplicityServices
git commit -m "iOS: OrganisationService"
```

---

### Task 2: SimplicityAdmin, the Settings screen and switching organisations

**Files:**
- Create: `ios/Packages/SimplicityAdmin/Package.swift`
- Create: `ios/Packages/SimplicityAdmin/Sources/SimplicityAdmin/Settings/SettingsViewModel.swift`
- Create: `ios/Packages/SimplicityAdmin/Sources/SimplicityAdmin/Settings/SettingsView.swift`
- Create: `ios/Packages/SimplicityAdmin/Sources/SimplicityAdmin/Resources/en.lproj/Localizable.strings`
- Modify: `ios/project.yml`
- Test: `ios/Packages/SimplicityAdmin/Tests/SimplicityAdminTests/SettingsViewModelTests.swift`

**Interfaces:**
- Consumes: `SessionService`, `OrganisationService`.
- Produces:
  - `@Observable @MainActor public final class SettingsViewModel` with `user: CurrentUserResponse?`, `organisations: [OrganisationMembershipResponse]`, `activeOrganisation`, `isOrgAdmin: Bool`, `isBusy`, `errorMessage`, `didSwitch: Bool`, `func load() async`, `func switchTo(_ orgId: UUID) async`, `func leave() async`
  - `public struct SettingsView: View { public init(onSignOut: @escaping () -> Void, onSwitched: @escaping () -> Void) }`

- [ ] **Step 1: Write the failing tests**

Cover:

- `isOrgAdmin` is true only when the **active** organisation's role is `ORG_ADMIN`. Write a case where the user is an admin of one organisation and a member of another, with the member one active — this is the bug that would otherwise show admin controls to somebody who cannot use them.
- `isOrgAdmin` is false when there is no active organisation.
- `switchTo` calls the service and updates the active organisation.
- `switchTo` sets `didSwitch`, which the shell uses to reset navigation — everything on screen belongs to the organisation being left.
- A failed switch leaves the previous active organisation in place and says so.
- `leave` calls the leave endpoint, not remove-member; a test that verifies the *other* call was not made, because confusing the two would silently remove a colleague.
- A refused leave — the sole administrator case — surfaces a distinct message rather than a generic failure.

- [ ] **Step 2: Run the tests to verify they fail**

- [ ] **Step 3: Write the view model and package manifest**

Map a 409 from `leave` to its own message. The generated client throws `ErrorResponse.error(statusCode, data, response, error)`, so match on the status code rather than the message text.

- [ ] **Step 4: Write the view**

A `List` with sections: the signed-in person's name and email; the organisation picker when they belong to more than one; links to Members, Teams and Invitations shown **only** when `isOrgAdmin`; then Leave this organisation and Sign out, both destructive and both confirmed.

The organisation picker shows each organisation's name and role. One organisation means no picker — a control with a single option is furniture.

- [ ] **Step 5: Run the tests to verify they pass**

- [ ] **Step 6: Commit**

```bash
git add ios/Packages/SimplicityAdmin ios/project.yml
git commit -m "iOS: Settings, and switching organisations"
```

---

### Task 3: Members

**Files:**
- Create: `ios/Packages/SimplicityAdmin/Sources/SimplicityAdmin/Members/MembersViewModel.swift`
- Create: `ios/Packages/SimplicityAdmin/Sources/SimplicityAdmin/Members/MembersView.swift`
- Test: `ios/Packages/SimplicityAdmin/Tests/SimplicityAdminTests/MembersViewModelTests.swift`

**Interfaces:**
- Consumes: `OrganisationService`, `SessionService`.
- Produces: `@Observable @MainActor public final class MembersViewModel` with `members`, `isLoading`, `isBusy`, `errorMessage`, `func load() async`, `func changeRole(_ member: OrgMemberResponse, to role: ChangeOrgRoleRequest.OrgRole) async`, `func remove(_ member: OrgMemberResponse) async`, `func isSelf(_ member: OrgMemberResponse) -> Bool`; and `public struct MembersView: View { public init() }`.

- [ ] **Step 1: Write the failing tests**

Cover:

- Members are listed in the server's order.
- `isSelf` is true for the signed-in user's own id — the row for yourself must not offer "remove", because removing yourself is *leaving* and has a different endpoint and a different consequence.
- Changing a role replaces that member in the list with the server's response, rather than mutating a local copy.
- Removing takes the member out of the list.
- A failed removal leaves the member in the list and says so; a list that lies about who has access is worse than an error.
- A failed role change does not show the new role.

- [ ] **Step 2: Run the tests to verify they fail**

- [ ] **Step 3: Write the view model**

- [ ] **Step 4: Write the view**

Each row: name (or email when they have not completed a profile), email, professional role, and a role picker. The row for yourself shows your role but no picker and no remove — you cannot demote or remove yourself here.

Removal confirms with the web's wording: *"Remove {name}? They will also leave every team in this organisation."*

- [ ] **Step 5: Run the tests to verify they pass**

- [ ] **Step 6: Commit**

```bash
git add ios/Packages/SimplicityAdmin
git commit -m "iOS: members and their roles"
```

---

### Task 4: Teams

**Files:**
- Create: `ios/Packages/SimplicityAdmin/Sources/SimplicityAdmin/Teams/TeamsViewModel.swift`
- Create: `ios/Packages/SimplicityAdmin/Sources/SimplicityAdmin/Teams/TeamsView.swift`
- Create: `ios/Packages/SimplicityAdmin/Sources/SimplicityAdmin/Teams/TeamDetailViewModel.swift`
- Create: `ios/Packages/SimplicityAdmin/Sources/SimplicityAdmin/Teams/TeamDetailView.swift`
- Test: `ios/Packages/SimplicityAdmin/Tests/SimplicityAdminTests/TeamsViewModelTests.swift`

**Interfaces:**
- Produces: `TeamsViewModel` (`teams`, `newTeamName`, `newTeamDescription`, `canCreate`, `load()`, `create()`, `delete(_:)`), `TeamDetailViewModel` (`init(teamId:teamName:)`, `members`, `candidates`, `load()`, `add(_:as:)`, `remove(_:)`), and their views.

`candidates` is the organisation's members minus those already in the team — the list an administrator picks from when adding somebody.

- [ ] **Step 1: Write the failing tests**

Cover:

- `canCreate` is false for a blank name, true otherwise.
- Creating appends the new team and clears the fields.
- A failed creation keeps the fields, so the typing is not lost.
- Deleting removes the team from the list.
- `candidates` excludes people already in the team — offering to add somebody twice invites a confusing server error.
- `candidates` is empty when every member is already in the team, and the view must say so rather than showing an empty picker.
- Adding a member moves them from `candidates` into `members` without a reload.
- Removing does the reverse.

- [ ] **Step 2: Run the tests to verify they fail**

- [ ] **Step 3: Write the view models**

- [ ] **Step 4: Write the views**

`TeamsView` lists teams with their member counts and a create form. Deleting confirms and names what it affects: a team's deletion unassigns its modules. `TeamDetailView` lists members with their team role and offers the candidates.

- [ ] **Step 5: Run the tests to verify they pass**

- [ ] **Step 6: Commit**

```bash
git add ios/Packages/SimplicityAdmin
git commit -m "iOS: teams and their membership"
```

---

### Task 5: Invitations

**Files:**
- Create: `ios/Packages/SimplicityAdmin/Sources/SimplicityAdmin/Invitations/InvitationsViewModel.swift`
- Create: `ios/Packages/SimplicityAdmin/Sources/SimplicityAdmin/Invitations/InvitationsView.swift`
- Test: `ios/Packages/SimplicityAdmin/Tests/SimplicityAdminTests/InvitationsViewModelTests.swift`

**Interfaces:**
- Produces: `InvitationsViewModel` with `invitations`, `teams`, `email`, `orgRole`, `teamId: UUID?`, `teamRole`, `canInvite`, `load()`, `invite()`, `revoke(_:)`.

- [ ] **Step 1: Write the failing tests**

Cover:

- `canInvite` is false for an empty address and for one with no `@`. Client-side email validation is otherwise not worth doing, but an invitation to a malformed address fails silently from the sender's point of view — they see it sent and nobody arrives.
- Inviting sends the chosen organisation role.
- Inviting with a team sends both the team and the team role.
- **Inviting without a team sends neither** — a team role without a team is meaningless and the server refuses the pair.
- A successful invitation appears in the list and clears the form.
- A failed invitation keeps the form.
- Revoking removes it from the list.
- An invitation already accepted cannot be revoked, and the view does not offer it.

- [ ] **Step 2: Run the tests to verify they fail**

- [ ] **Step 3: Write the view model**

- [ ] **Step 4: Write the view**

Each row: the address, the role, the team if any, the status, and when it expires. Only `PENDING` invitations offer revoke. Status wording is plain: Pending, Accepted, Revoked, Expired.

- [ ] **Step 5: Run the tests to verify they pass**

- [ ] **Step 6: Commit**

```bash
git add ios/Packages/SimplicityAdmin
git commit -m "iOS: invitations"
```

---

### Task 6: Wire Settings into the shell

**Files:**
- Modify: `ios/Simplicity_iOS/Content/MainTabView.swift`
- Modify: `ios/project.yml`

- [ ] **Step 1: Extend the smoke test**

Assert the signed-out app still reaches sign-in, as before.

- [ ] **Step 2: Replace the Settings placeholder**

`SettingsView` in its own `NavigationStack`, with destinations for Members, Teams, Team detail and Invitations. Sign out stays here.

On `onSwitched`, reset the Dashboard and Learn navigation paths — everything in them belongs to the organisation just left.

- [ ] **Step 3: Run the tests to verify they pass**

- [ ] **Step 4: Commit**

```bash
git add ios
git commit -m "iOS: Settings in the shell"
```

---

### Task 7: Verify against production

**Files:**
- Create: `scripts/verify_ios_org_admin.py`

- [ ] **Step 1: Write the check**

Using `Run` and `run.organisation(...)` so cleanup archives what it makes:

1. An administrator creates an organisation and a team.
2. Invite a second address; assert the invitation is `PENDING` and appears in the list.
3. Revoke it; assert the status becomes `REVOKED` and it can no longer be accepted.
4. Invite a second real account and have it accept; assert it appears in members as `ORG_MEMBER`.
5. Promote it to `ORG_ADMIN`; assert the role changed. Demote it back.
6. Add it to the team; assert it appears in team members. Remove it; assert it does not.
7. **Assert a plain member gets 403 listing members** — the whole point of the role, and the thing the app's hidden controls are only cosmetic about.
8. Remove the member from the organisation; assert their `/me` no longer lists it.
9. Assert the sole remaining administrator cannot leave, or — if the server permits it — record what actually happens, because the app's error message depends on which it is.

Step 9 is written to *discover* the behaviour rather than assert a guess: the app needs to say something accurate, and only production can settle it.

- [ ] **Step 2: Run it**

- [ ] **Step 3: Drive the app on a simulator**

Sign in as the administrator, invite somebody, create a team, switch organisations, and confirm the tabs reload rather than showing the previous organisation's modules.

- [ ] **Step 4: Commit**

```bash
git add scripts/verify_ios_org_admin.py
git commit -m "iOS: verify organisation administration against production"
```

---

## Self-review against the spec

| Spec requirement | Covered by |
| --- | --- |
| §2.1 full parity, administration included | Tasks 2 to 5; authoring is the next plan |
| §4 administration behind the same role check as the web | Task 2's `isOrgAdmin`, from the active organisation |
| §4 the tab does not advertise what a clinician cannot open | Task 2 — links hidden for non-administrators |
| Organisation switching | Task 2, including resetting navigation |
| §2.7 honest failure | Every view model has an `errorMessage`, and each task tests a failure path that keeps the user's typing |

Two gaps carried deliberately. **Module authoring, team assignment and video upload** are the next plan — they are half the admin surface on their own. And **a 403 arriving anyway**, because an administrator was demoted in another session while this one had the controls on screen, is handled as an ordinary error rather than by re-checking the session and redrawing; doing that properly needs a session-refresh-on-403 path that belongs with the token work, not here.
