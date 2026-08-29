# Organisation Switching and Archiving — Design

Date: 2026-08-29
Status: approved, not yet implemented
Follows: [Phase 1](2026-08-29-digital-health-app-phase1-design.md)

## 1. Why

Phase 1 built a data model where a clinician may belong to several organisations, and a client that
quietly ignores all but the first. `SessionService.activeOrganisation` returns `organisations()[0]`
and five screens read it, so a locum working across two hospitals can see only one of them and has
no way to say which.

The same gap runs the other way. An administrator can remove somebody, but nobody can leave of
their own accord, and an organisation created by mistake during self-signup is permanent.

## 2. Definition of done

A clinician belonging to more than one organisation can switch between them from the masthead, and
the choice follows them to another device. Anybody can leave an organisation; when the last
administrator leaves, the organisation is archived rather than orphaned. An administrator can
archive an organisation deliberately. An archived organisation is unreachable by every member,
including by direct id, and its history survives.

## 3. Decisions and their rationale

### 3.1 Archiving, not deletion

`audit_event` is append-only and exists so that every membership and role change can be accounted
for afterwards. Phase 3 adds clinicians' reflections on their own practice. Deleting an
organisation's rows would destroy the record most worth keeping — the one that answers "who removed
whom, and when" — in service of a button most often pressed to tidy up a mistake.

This is not hypothetical: `audit_event.org_id` is declared `ON DELETE CASCADE`, so deleting an
organisation row silently takes its entire history with it. Archiving sidesteps that rather than
requiring the schema to be loosened.

So `DELETE /api/v1/orgs/{orgId}` sets `archived_at`. Memberships, teams, invitations, and audit
events all remain. Genuine erasure, if a regulator or a customer ever demands it, is a separate
operation against a known-quiet organisation, and is out of scope here.

### 3.2 The last administrator may leave, and takes the organisation with them

Removal already refuses to strip the last administrator, and that stays: removing the last admin is
almost always a mistake, and refusing costs the caller one promotion.

Leaving is different. It is a deliberate act by the person themselves, and blocking it would trap
somebody in an organisation they created by accident with nobody to promote. When the last
administrator leaves, the organisation is archived in the same transaction. The two paths differ
because the intent differs, not because the state does.

### 3.3 The active organisation lives on the user record

Storing it in `sessionStorage` beside the tokens would be cheaper, but it would reset every time a
tab closes and disagree between a laptop and an iPad. Phase 5 adds native clients that will read
the same value, so it belongs on the server: one nullable column and one endpoint.

### 3.4 DELETE, although nothing is deleted

`DELETE /api/v1/orgs/{orgId}` reads naturally in three generated clients and matches what the
caller believes they are doing. The description in the API document says it archives. Should true
erasure ever arrive it gets its own explicit route rather than a flag on this one.

### 3.5 Archived organisations keep their slug

`organisation.slug` is globally unique and archiving does not release it. Recreating an
organisation by the same name yields a suffixed slug, which the existing generator already handles
for live duplicates. Freeing the slug would mean either mutating an archived row or a partial
unique index, and neither earns its complexity for a URL fragment nobody has yet seen.

## 4. Schema

Migration `V2__organisation_archiving_and_active_org.sql`:

```sql
ALTER TABLE organisation
    ADD COLUMN archived_at TIMESTAMPTZ,
    ADD COLUMN archived_by UUID REFERENCES app_user (id);

ALTER TABLE app_user
    ADD COLUMN active_org_id UUID REFERENCES organisation (id);
```

No index accompanies these. Archived organisations are filtered while building a principal, which
is a lookup by primary key through an existing membership, and the table holds a handful of rows at
pilot scale. An index on `archived_at` would be answering a query nobody makes.

Both columns are nullable and no backfill is required: every existing organisation is live, and
every existing user falls back to their first membership.

`active_org_id` is a preference, not an authorisation input. It is never trusted on its own — the
membership is checked on every request regardless, exactly as now.

## 5. API surface

| Method | Path | Authorisation | Effect |
| --- | --- | --- | --- |
| `DELETE` | `/api/v1/orgs/{orgId}` | `ORG_ADMIN` | Archives the organisation |
| `DELETE` | `/api/v1/orgs/{orgId}/members/me` | Any member | Leaves; archives if the caller was the last admin |
| `PUT` | `/api/v1/me/active-organisation` | Authenticated | Sets the preferred organisation |

`PUT /api/v1/me/active-organisation` takes `{"organisationId": "..."}` and returns the updated
`CurrentUserResponse`. It responds 403 for any organisation that is not a live membership of the
caller's, whether that is because they never belonged, because they have left, or because it has
been archived. The three are deliberately indistinguishable: an archived organisation is excluded
from the principal, so the server cannot tell them apart either, and a caller learning which of the
three applied would be learning about an organisation they cannot reach.

`CurrentUserResponse` gains `activeOrganisationId`. `CurrentUserService.describe` omits archived
organisations from `organisations`, and resolves `activeOrganisationId` by falling back to the
first remaining membership whenever the stored value is absent, archived, or no longer the
caller's. A stale pointer therefore cannot strand anybody on an empty screen.

Every mutation writes an audit event, named to match the existing `ORGANISATION_CREATED` and
`ORG_MEMBER_REMOVED`: `ORGANISATION_ARCHIVED` and `ORG_MEMBER_LEFT`.

## 6. Authorisation and the principal cache

Authorisation reads `AppPrincipal.orgRoles`, so the archive filter belongs where the principal is
built. `PrincipalService.forUser` excludes memberships whose organisation is archived, and every
existing `@PreAuthorize("@authz.isOrgMember(#orgId)")` then refuses an archived organisation
without being touched. Repository queries continue to filter on `org_id` as the second layer.

The cache makes this a fan-out. `AppPrincipal` lives in Redis for five minutes, so archiving must
evict **every member's** entry, not just the actor's — otherwise colleagues keep working access to
an archived organisation for up to five minutes, which is precisely the window archiving exists to
close. `OrganisationService.archive` therefore iterates the memberships and calls
`SessionService.rolesChanged(userId)` for each. Leaving evicts only the person leaving.

This is the first mutation whose eviction is not confined to the caller, and it is the part most
likely to be got wrong later, so it gets an explicit test rather than only a passing mention.

## 7. Client

`SessionService.activeOrganisation` stops meaning "the first one" and reads `activeOrganisationId`,
still falling back to the first membership so the signal is never null while a membership exists.
A new `setActiveOrganisation(id)` calls the endpoint, replaces the cached user, and navigates to
the dashboard so that org-scoped screens reload against the new organisation rather than showing
the previous one's teams.

The masthead in `shell.ts` gains a switcher, rendered only when the clinician belongs to more than
one organisation, so the common case is unchanged.

Settings gains two actions. "Leave organisation" is available to everyone and warns the last
administrator that leaving will archive it. "Archive organisation" is admin-only and requires
typing the organisation's name, because it affects colleagues rather than just the person clicking.
Leaving the only organisation returns the clinician to `/welcome/organisation`.

## 8. Testing

Failing-first, and weighted towards the negative cases:

- A former member holding a valid organisation id receives 403 after it is archived.
- A member's cached principal does not survive an archive performed by somebody else.
- Switching to an organisation the caller does not belong to is refused.
- An active organisation pointing at an archived organisation falls back rather than erroring.
- The last administrator leaving archives the organisation; an administrator removing the last
  administrator is still refused.
- Archiving writes an audit event naming both the actor and the organisation.

## 9. Out of scope

Hard erasure, restoring an archived organisation, transferring ownership, and per-organisation
data export. Each is a deliberate operation with its own consequences, and none is needed to close
the gap this document describes.

## 10. Risks

| Risk | Mitigation |
| --- | --- |
| Eviction fan-out missed, leaving colleagues with access to an archived organisation | Explicit test for a second member's cached principal |
| A clinician archives an organisation believing it deletes their data | Confirmation names the organisation and says the history is retained |
| Archived organisations accumulate invisibly | Acceptable at pilot scale; revisit when a support view exists |
