# Phase 2a — Modules and Learning — Design

Date: 2026-08-29
Status: approved, not yet implemented
Follows: [Phase 1](2026-08-29-digital-health-app-phase1-design.md)

## 1. Scope

Phase 2 as originally sketched — authoring, video, quizzes, assignment, and two learner tabs — is
six subsystems. It is split into three:

| | Scope |
| --- | --- |
| **2a (this spec)** | Modules with text sections, versioned publishing, assignment to teams, the Learn tab, progress, and the Dashboard |
| 2b | Video and file attachments: S3 upload, presigned playback, media in sections |
| 2c | Quizzes: questions, options, attempts, pass marks, completion gated on passing |

2a is the smallest slice that puts something in front of a clinician. Video and quizzes enrich a
loop that has to exist first.

### Definition of done

An organisation administrator writes a module, publishes it, and assigns it to a team. Everyone in
that team sees it in Learn, works through its sections, and finishes it. The Dashboard shows each
clinician what is outstanding and what is done. When the administrator publishes a substantive
revision, the module becomes outstanding again for people who had completed the previous one, and
their earlier completion remains on record against the version they actually completed.

## 2. Decisions and their rationale

### 2.1 Content is Markdown

An administrator authoring HTML that renders in colleagues' browsers is stored cross-site
scripting with extra steps, and the people with authoring rights are precisely the ones whose
account is worth stealing. Sections store Markdown; clients render it through a sanitiser.

It also travels. Phase 5 adds Swift and Kotlin clients that must render the same content, and
Markdown has a renderer everywhere while a bespoke HTML subset would need re-implementing three
times.

### 2.2 A published version is immutable; editing creates a new one

`module` holds identity, `org_id`, and title. `module_version` holds status, a version number, and
the sections. Editing a published module creates a fresh draft rather than mutating what people
have already read, which is what lets a completion mean something specific.

At most one draft exists per module at a time. A second concurrent draft would need merge
semantics to resolve, and nobody has asked for two administrators to revise the same module
simultaneously.

### 2.3 Publishing asks whether the change was substantive

Versioned completions are only useful if the product can tell "we fixed a typo" from "we rewrote
the protocol". Forcing two hundred clinicians to redo training because a comma moved would train
them to click through it; never asking would leave a rewritten module silently marked complete.

So `module_version.supersedes_completions` is set by the administrator at publish time. When true,
anyone whose most recent completion is against an earlier version sees the module as outstanding
again. Their old completion row is not touched: it remains a true statement about a version that
really existed.

### 2.4 Progress is recorded per section

A module is several sections long and a clinician will not finish one in a sitting. Per-section
progress lets Learn resume where they left off and lets the Dashboard say how far through they
are, rather than only whether they finished. The module's own status is derived: complete when
every section of that version is complete.

Self-report is the completion signal in 2a because there is nothing else to go on yet. 2c replaces
it with passing a quiz, which is why module completion is computed rather than stored as a flag a
client sets.

### 2.5 Visibility follows team assignment

Modules reach clinicians through `team_module_assignment`, as Phase 1 committed. A clinician sees a
module when it is assigned to a team they are in. An administrator sees every module in their
organisation, because they have to be able to author one before anybody is assigned it.

This is the third layer of the existing org-scoping rather than a new mechanism: the principal
already carries team memberships, so the check is a set intersection against data the request
already has.

## 3. Schema

Migration `V3__modules_and_progress.sql`:

```sql
CREATE TABLE module (
    id          UUID PRIMARY KEY,
    org_id      UUID        NOT NULL REFERENCES organisation (id) ON DELETE CASCADE,
    title       TEXT        NOT NULL CHECK (length(trim(title)) > 0),
    summary     TEXT,
    created_by  UUID        REFERENCES app_user (id) ON DELETE SET NULL,
    archived_at TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE module_version (
    id                      UUID PRIMARY KEY,
    module_id               UUID        NOT NULL REFERENCES module (id) ON DELETE CASCADE,
    version_number          INT         NOT NULL,
    status                  TEXT        NOT NULL CHECK (status IN ('DRAFT', 'PUBLISHED')),
    supersedes_completions  BOOLEAN     NOT NULL DEFAULT false,
    published_at            TIMESTAMPTZ,
    published_by            UUID        REFERENCES app_user (id) ON DELETE SET NULL,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (module_id, version_number)
);

-- At most one draft per module: a second would need merge semantics nobody has asked for.
CREATE UNIQUE INDEX idx_module_single_draft
    ON module_version (module_id) WHERE status = 'DRAFT';

CREATE TABLE module_section (
    id         UUID PRIMARY KEY,
    version_id UUID NOT NULL REFERENCES module_version (id) ON DELETE CASCADE,
    position   INT  NOT NULL,
    title      TEXT NOT NULL CHECK (length(trim(title)) > 0),
    body       TEXT NOT NULL DEFAULT '',
    UNIQUE (version_id, position)
);

CREATE TABLE team_module_assignment (
    team_id     UUID        NOT NULL REFERENCES team (id) ON DELETE CASCADE,
    module_id   UUID        NOT NULL REFERENCES module (id) ON DELETE CASCADE,
    assigned_by UUID        REFERENCES app_user (id) ON DELETE SET NULL,
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (team_id, module_id)
);

-- Pinned to the version, not the module. This is the whole point of versioning, and it has to be
-- true of the very first row written: back-filling completions against versions nobody recorded
-- is not possible after the fact.
CREATE TABLE user_section_progress (
    user_id      UUID        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    section_id   UUID        NOT NULL REFERENCES module_section (id) ON DELETE CASCADE,
    completed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, section_id)
);

CREATE TABLE user_module_completion (
    user_id      UUID        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    version_id   UUID        NOT NULL REFERENCES module_version (id) ON DELETE CASCADE,
    completed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, version_id)
);

CREATE INDEX idx_module_org ON module (org_id) WHERE archived_at IS NULL;
CREATE INDEX idx_assignment_module ON team_module_assignment (module_id);
CREATE INDEX idx_section_progress_user ON user_section_progress (user_id);
```

`user_module_completion` is append-only in spirit: a completion is never updated or removed, only
added for a later version. That is what makes "completed version 2 on 4 June, version 3 outstanding
since 11 August" expressible.

## 4. API surface

Authoring, all `ORG_ADMIN`:

| Method | Path | Effect |
| --- | --- | --- |
| `POST` | `/api/v1/orgs/{orgId}/modules` | Creates a module with an empty draft version 1 |
| `GET` | `/api/v1/orgs/{orgId}/modules` | Lists modules with their draft and published state |
| `GET` | `/api/v1/orgs/{orgId}/modules/{moduleId}` | One module, both versions, sections included |
| `PATCH` | `/api/v1/orgs/{orgId}/modules/{moduleId}` | Retitles; does not touch versions |
| `DELETE` | `/api/v1/orgs/{orgId}/modules/{moduleId}` | Archives, as organisations do |
| `POST` | `/api/v1/orgs/{orgId}/modules/{moduleId}/draft` | Opens a draft copying the published version |
| `PUT` | `/api/v1/orgs/{orgId}/modules/{moduleId}/draft/sections` | Replaces the draft's sections wholesale |
| `POST` | `/api/v1/orgs/{orgId}/modules/{moduleId}/draft/publish` | Publishes, taking `supersedesCompletions` |
| `PUT` | `/api/v1/orgs/{orgId}/modules/{moduleId}/teams` | Sets which teams it is assigned to |

Sections are replaced wholesale rather than patched individually. Reordering and editing arrive
together from a single authoring screen, and a per-section API would need positions reconciled
across several round trips for no gain.

Learning, any organisation member:

| Method | Path | Effect |
| --- | --- | --- |
| `GET` | `/api/v1/orgs/{orgId}/learning` | Modules assigned to the caller's teams, with progress |
| `GET` | `/api/v1/orgs/{orgId}/learning/{moduleId}` | The published version and its sections |
| `PUT` | `/api/v1/orgs/{orgId}/learning/sections/{sectionId}/complete` | Marks one section done |

Marking the final section writes the `user_module_completion` row in the same transaction, so a
client cannot leave a module in a state where every section is done but the module is not.

Audit events: `MODULE_CREATED`, `MODULE_PUBLISHED`, `MODULE_ARCHIVED`, `MODULE_ASSIGNED`. Learner
progress is deliberately not audited — it is ordinary use, it happens constantly, and the audit
table exists for decisions about people rather than for reading.

## 5. Authorisation

Authoring endpoints reuse `@authz.isOrgAdmin(#orgId)`. Learning endpoints use
`@authz.isOrgMember(#orgId)` plus an assignment check inside the service: the module must be
assigned to a team the caller belongs to. `AppPrincipal` already carries team memberships, so this
is a set intersection rather than a query.

The negative case is the one worth testing: a member of an organisation who is in no team assigned
a given module must not be able to read it by id, even though `isOrgMember` passes.

Archived modules and unpublished drafts are invisible to learners for the same reason archived
organisations are invisible: they are filtered in the service that serves them, not merely omitted
from a list.

## 6. Client

**Learn** lists the caller's assigned modules with a progress bar and a status of not started, in
progress, complete, or needs redoing. Opening one shows its sections in order with a "mark as read"
control, resuming at the first incomplete section.

**Dashboard** shows the count outstanding and the next module to pick up, replacing the empty
placeholder Phase 1 left.

**Settings** gains an admin-only Modules screen: the list, an editor with title, summary, and
ordered Markdown sections, a publish control asking whether the change is substantive, and team
assignment. The editor works on the draft only, and the published version is shown read-only
beside it so an author can see what learners currently have.

Markdown is rendered with a sanitiser configured to strip scripts, event handlers, and inline
styles. Nothing an author writes reaches a colleague's browser unfiltered.

## 7. Testing

- A clinician in an unassigned team cannot read a module by id.
- A clinician in another organisation cannot read it at all.
- A draft is invisible to learners; publishing makes it visible.
- Completing the last section completes the module in the same transaction.
- Publishing with `supersedesCompletions` makes the module outstanding again for somebody who had
  completed the previous version, and leaves their earlier completion row untouched.
- Publishing without it leaves them complete.
- A second draft cannot be opened while one exists.
- Section positions survive a reorder that also renames and deletes sections.

## 8. Out of scope

Video and attachments (2b), quizzes (2c), due dates, reminders, per-clinician assignment,
cross-organisation content sharing, and reporting beyond a clinician's own progress. An
administrator seeing how their team is progressing is a natural next request and is deliberately
not in this slice.

## 9. Risks

| Risk | Mitigation |
| --- | --- |
| Markdown rendering becomes an injection route | Sanitise at render on every client; never store HTML |
| Wholesale section replacement loses concurrent edits | One draft per module, enforced by a partial unique index |
| Administrators tick "substantive" every time and train people to click through | Default false, and the control says what it does to colleagues |
| Progress rows grow per user per section | Rows are tiny and bounded by content; revisit if a module ever exceeds a few dozen sections |
