# Phase 4 — Assistant — Design

Date: 2026-08-29
Status: approved, not yet implemented
Follows: [Phase 2a](2026-08-29-phase2a-modules-and-learning-design.md)

## 1. Scope

A clinician asks a question and gets an answer drawn from their organisation's published training
modules, with citations. When the modules do not cover it, it says so.

Single turn. No conversation history, no follow-ups that depend on what was asked before.

### Definition of done

A clinician asks something the training covers and gets an answer citing the module and section it
came from. They ask something it does not cover and are told so rather than given a guess. Nothing
they have written in Reflect is involved in any of it.

## 2. Decisions and their rationale

### 2.1 It answers from the modules or it does not answer

This is a mental health product, and an assistant inside it will be asked clinical questions —
what to do about a disclosure of suicidal ideation, whether a presentation warrants referral —
because it is there and it sounds like it knows. Those questions have no answer in the training
content. A model permitted to fall back on general knowledge will answer anyway, fluently, to a
clinician who may act on it.

So retrieval decides whether there is an answer at all. Below a similarity threshold the request
returns `answered: false` with wording that points to supervision, and no generated text. The
system prompt forbids drawing on anything but the supplied passages, but the threshold is the
control that matters, because a prompt is a request and a threshold is a gate.

Every answer cites the module and section behind it, so a clinician can check rather than trust.

### 2.2 Reflections are never read

Phase 3 promised that nobody reads a clinician's journal, including the server. Sending reflections
to Bedrock would make that untrue. Bedrock does not train on inputs and stays in region, but it is
still content leaving the database to be read by something else, and the promise was not "read only
by systems we approve of".

There is no code path from reflections to the assistant. That is worth stating because the feature
somebody will eventually ask for — help me reflect on this — is precisely the one that breaks it.

### 2.3 Retrieval covers the whole organisation, not just assigned modules

Within an organisation, training content is not secret; assignment says what a clinician is
expected to work through, not what they may know about.

The consequence is that the assistant can cite a module a clinician cannot open in Learn. Rather
than hide that, a citation carries whether the module is assigned to them, and the client links only
the ones they can actually reach. Archived modules and unpublished drafts are excluded regardless —
a draft is not content, and an archive is meant to be unreachable.

### 2.4 Bedrock, in region

`amazon.titan-embed-text-v2` for embeddings, invoked directly in `ap-southeast-2`, and Claude Haiku
4.5 for generation through the `au.` inference profile. Bedrock refuses Claude on demand without a
profile, and the choice of profile is a data residency decision rather than a formality: `global.`
and `apac.` route wherever there is capacity, while `au.` routes only to `ap-southeast-2` and
`ap-southeast-4`. Training content therefore stays in Australia, though not in a single region.

Everything stays under the existing AWS agreement, and the task role authenticates instead of
another API key in Secrets Manager.

Haiku rather than Sonnet: the model is summarising supplied passages, not reasoning from scratch,
which is the job small models do well and cheaply.

### 2.5 pgvector in the database that already exists

Phase 1 flagged that Neptune starts around 200 to 700 US dollars a month, several times this whole
stack, and said to measure `pgvector` first. At this size the measurement is not close: an
organisation's training content is thousands of chunks, and Postgres with an HNSW index answers
that in single-digit milliseconds.

The one operational consequence: `postgres:17-alpine` has no `pgvector`, so the test containers
move to `pgvector/pgvector:pg17`. RDS supports the extension already.

### 2.6 Indexing is asynchronous, so publishing never depends on Bedrock

Embedding happens after publishing, driven by a scheduled job over versions with no `indexed_at`,
in the same shape as the media poller.

Doing it inside the publish request would be simpler and wrong: an administrator could not publish
a module while Bedrock was unavailable. Publishing is the product working; the assistant knowing
about it a minute later is not.

## 3. Schema

Migration `V8__assistant.sql`:

```sql
CREATE EXTENSION IF NOT EXISTS vector;

ALTER TABLE module_version ADD COLUMN indexed_at TIMESTAMPTZ;

CREATE TABLE module_chunk (
    id            UUID PRIMARY KEY,
    org_id        UUID        NOT NULL REFERENCES organisation (id) ON DELETE CASCADE,
    module_id     UUID        NOT NULL REFERENCES module (id) ON DELETE CASCADE,
    version_id    UUID        NOT NULL REFERENCES module_version (id) ON DELETE CASCADE,
    section_id    UUID REFERENCES module_section (id) ON DELETE SET NULL,
    module_title  TEXT        NOT NULL,
    section_title TEXT,
    content       TEXT        NOT NULL,
    embedding     VECTOR(1024) NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_module_chunk_embedding
    ON module_chunk USING hnsw (embedding vector_cosine_ops);
CREATE INDEX idx_module_chunk_org ON module_chunk (org_id);
CREATE INDEX idx_module_version_unindexed
    ON module_version (id) WHERE status = 'PUBLISHED' AND indexed_at IS NULL;
```

Chunks hang off a version, which is immutable once published, so a chunk can never describe content
that has since changed. Superseded versions are re-indexed as new rows and the old ones removed when
their version is.

1024 dimensions is the Titan v2 default.

## 4. Retrieval and generation

1. Embed the question.
2. Take the six nearest chunks within the organisation, excluding archived modules and any version
   that is not the current published one.
3. If the nearest is further than the threshold, return `answered: false` and stop. No model call,
   so an unanswerable question costs nothing.
4. Otherwise send the passages and the question to Haiku with a prompt that permits only what the
   passages contain.
5. Return the answer with a citation per module and section used.

Rate limited through the existing Redis limiter at thirty questions an hour per clinician, which is
far above use and far below a bill worth noticing.

## 5. API surface

| Method | Path | Authorisation |
| --- | --- | --- |
| `POST` | `/api/v1/orgs/{orgId}/assistant/questions` | `@authz.isOrgMember(#orgId)` |

Request is a question; response is `answered`, `answer`, and `citations`, each carrying the module
id, module title, section title, and whether that module is assigned to the caller.

## 6. Client

An ask box on the Learn tab rather than a fifth tab, which would mean reworking the shell for a
feature nobody has used yet.

The answer appears below with its citations. Citations for assigned modules link into Learn; the
rest are named without a link, so a clinician is not sent somewhere they cannot go.

Framing throughout is "ask about the training", never "assistant", and the unanswered response says
plainly that the training does not cover it and that supervision is the right route. A clinician
should never be left with the impression that this is a source of clinical guidance.

## 7. Testing

- A question about indexed content returns an answer with a citation naming the right module.
- A question about something absent returns `answered: false` and calls no model.
- Retrieval never crosses organisations.
- Archived modules and unpublished drafts are not retrieved.
- Republishing a module re-indexes it, and the superseded chunks stop being returned.
- The rate limit refuses the thirty-first question in an hour.
- No code path reads a reflection: asserted by there being no dependency from the assistant on the
  reflection repository.

Bedrock is stubbed behind the interfaces the service depends on, as MediaConvert is. The embedding
fake returns deterministic vectors so nearest-neighbour ordering is assertable.

## 8. Out of scope

Conversation history and follow-up questions, streaming responses, feedback on answers, any use of
reflections, and administrator visibility into what was asked. The last is worth naming: knowing
which questions clinicians ask would be genuinely useful for improving modules, and it is also
surveillance of people learning. It needs its own decision, not a quiet addition.

## 9. Risks

| Risk | Mitigation |
| --- | --- |
| Treated as clinical guidance | Refuses outside the training content, cites sources, framed as asking about training |
| A confident answer from thin retrieval | Threshold before any model call; citations let a clinician check |
| Content cited that the clinician cannot open | Citations carry whether it is assigned; unassigned ones are named, not linked |
| Cost drifting with use | Thirty an hour per clinician, and no model call at all when retrieval is empty |
| Embeddings drifting from content | Chunks belong to an immutable version and are replaced on republish |
