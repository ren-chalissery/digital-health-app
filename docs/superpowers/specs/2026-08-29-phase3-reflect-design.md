# Phase 3 — Reflect — Design

Date: 2026-08-29
Status: approved, not yet implemented
Follows: [Phase 1](2026-08-29-digital-health-app-phase1-design.md)

## 1. Scope

A private journal. A clinician writes reflections on their own practice, searches them, edits them,
and deletes them. Nobody else can read them.

This fills the last empty tab and is the first place clinicians write content of their own.

### Definition of done

A clinician writes a reflection, finds it later by searching for a word in it, edits it, and deletes
it. Another clinician holding its id gets nothing. The editor warns, without blocking, when what
has been written looks like it identifies a patient.

## 2. Decisions and their rationale

### 2.1 A reflection belongs to a person, not an organisation

Phase 1 committed that a reflection belongs to a single user. Following that through: there is no
`org_id` on the table at all.

The consequences are deliberate. Reflections survive leaving an organisation and survive an
organisation being archived, because they are the clinician's notes on their own practice rather
than the organisation's records. A clinician working across two organisations has one journal, not
two. No administrator can reach them, because there is no organisation-scoped route by which they
could.

### 2.2 The server does not read them either

Detection of patient identifiers runs in the client and warns before saving. The server stores what
it is given and never inspects it.

This follows from the privacy model rather than from convenience. Having the server analyse a
private journal and record a judgement about its contents — even a flag — would undercut the
promise that only the author reads it, and would create exactly the field somebody later asks to
report on.

The cost is real and worth stating: Phase 5's Swift and Kotlin clients each need their own copy of
the patterns, so the rules exist three times. That is a maintenance cost, not a correctness one —
each client warns its own user, and none of them is a security control.

### 2.3 Warn, never block

A blocking filter teaches evasion. Refused a full name and an NHI number, somebody writes "J.S.,
DOB 12/3" instead — which the filter cannot see, which is still identifying, and which now carries
the false assurance that the field was checked.

A warning that explains why the product asks is more likely to change what gets written. It names
what it spotted, and saving anyway takes one more click and no argument.

Detected: New Zealand NHI numbers, Australian Medicare numbers, dates that read as birthdates,
email addresses, and phone numbers. Deliberately not names, which cannot be detected without
either a dictionary that flags every clinical term or a model that reads the journal.

### 2.4 Full text search in Postgres

A generated `tsvector` column with a GIN index. Nothing else is warranted: search is scoped to one
person's own writing, which is hundreds of rows rather than millions, and OpenSearch would be a
second datastore to run, secure, and keep in step for a feature one clinician uses at a time.

### 2.5 Encryption is the disk, not the column

RDS encrypts at rest and TLS covers transit. Encrypting the body column itself would make the
`tsvector` impossible — you cannot index what you cannot read — so the choice is real search or
column encryption, and search is the feature.

That places the confidentiality boundary at the database, which is where the Phase 1 spec already
put it. It also means the standing warning in that spec still applies: the compliance position and
the decision to run without a NAT gateway both rest on these reflections being de-identified.

## 3. Schema

Migration `V7__reflections.sql`:

```sql
CREATE TABLE reflection (
    id         UUID PRIMARY KEY,
    -- Cascades: a deleted person's private journal goes with them.
    user_id    UUID        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    title      TEXT,
    body       TEXT        NOT NULL CHECK (length(trim(body)) > 0),
    search     TSVECTOR GENERATED ALWAYS AS (
                   to_tsvector('english', coalesce(title, '') || ' ' || body)
               ) STORED,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_reflection_search ON reflection USING GIN (search);
CREATE INDEX idx_reflection_user ON reflection (user_id, created_at DESC);
```

Generated rather than maintained by a trigger or by the application, so the index cannot drift from
the text it indexes.

## 4. API surface

Every route is under `/api/v1/me`, because these belong to the caller and to nobody else.

| Method | Path | Effect |
| --- | --- | --- |
| `GET` | `/api/v1/me/reflections?q=` | The caller's own, newest first, or matching a search |
| `POST` | `/api/v1/me/reflections` | Writes one |
| `GET` | `/api/v1/me/reflections/{id}` | Reads one |
| `PUT` | `/api/v1/me/reflections/{id}` | Edits one |
| `DELETE` | `/api/v1/me/reflections/{id}` | Deletes one |

Every query is filtered by the caller's user id, and a reflection belonging to somebody else
returns **404 rather than 403**. A 403 would confirm that the id names something real, which is
itself a disclosure about a private journal.

Search uses `plainto_tsquery`, so a clinician types words rather than operators, and results are
ranked by `ts_rank`.

## 5. Client

The Reflect tab lists entries newest first with a search box. Writing opens a plain editor: an
optional title and a body.

Below the body sits the identifier warning, which appears as they type and names what it found —
"this looks like it contains an NHI number" — with a sentence on why the product asks for
reflections without them. Saving is never blocked.

The tab's empty state carries the same guidance before anybody has written anything, which is the
cheapest moment to set the expectation.

Bodies are plain text, not Markdown. Nobody but the author reads a reflection, so formatting buys
nothing, and rendering author-written markup is a surface with no benefit here.

## 6. Testing

- Another clinician's reflection id returns 404, not 403.
- The list contains only the caller's own.
- Search finds a word in the body and one in the title, and misses a word in neither.
- Search does not reach another clinician's entries.
- Editing updates what search finds, which is what proves the generated column is generated.
- Deleting a user takes their journal.
- The identifier patterns are unit tested, including what they must not flag: clinical terms,
  dosages, and times of day.

## 7. Out of scope

Sharing with a supervisor, exporting, attachments, tags, reflections linked to modules, and any
administrator view. The first two are plausible next requests; the last is deliberately absent, and
adding it later would be a change to the promise this feature makes rather than a feature on top of
it.

## 8. Risks

| Risk | Mitigation |
| --- | --- |
| Identifiers written despite the warning | Warning names what it found and says why; the compliance posture assumes de-identified text and the Phase 1 spec says to reassess if that changes |
| The warning trains people to dismiss it | It only appears on a match, never on every save |
| Search misleads by ranking badly | `ts_rank` over one person's own writing; revisit only if a clinician has enough entries for ranking to matter |
| Someone later asks for an administrator view | Named as out of scope here, because granting it silently would break what clinicians were told |
