-- A private journal. No org_id: a reflection is the clinician's notes on their own practice, so it
-- survives leaving an organisation and survives one being archived, and there is no
-- organisation-scoped route by which an administrator could reach it.

CREATE TABLE reflection (
    id         UUID PRIMARY KEY,
    -- Cascades: a deleted person's private journal goes with them.
    user_id    UUID        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    title      TEXT,
    body       TEXT        NOT NULL CHECK (length(trim(body)) > 0),
    -- Generated rather than maintained by a trigger or by the application, so what is indexed
    -- cannot drift from what was written.
    search     TSVECTOR GENERATED ALWAYS AS (
                   to_tsvector('english', coalesce(title, '') || ' ' || body)
               ) STORED,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_reflection_search ON reflection USING GIN (search);
CREATE INDEX idx_reflection_user ON reflection (user_id, created_at DESC);
