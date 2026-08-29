-- Training content. A module is identity and ownership; its sections live on a version, so that
-- publishing produces something immutable for a completion to point at.

CREATE TABLE module (
    id          UUID PRIMARY KEY,
    org_id      UUID        NOT NULL REFERENCES organisation (id) ON DELETE CASCADE,
    title       TEXT        NOT NULL CHECK (length(trim(title)) > 0),
    summary     TEXT,
    created_by  UUID REFERENCES app_user (id) ON DELETE SET NULL,
    archived_at TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE module_version (
    id                     UUID PRIMARY KEY,
    module_id              UUID        NOT NULL REFERENCES module (id) ON DELETE CASCADE,
    version_number         INT         NOT NULL,
    status                 TEXT        NOT NULL CHECK (status IN ('DRAFT', 'PUBLISHED')),
    -- Set by whoever publishes, because only they know whether the change was a corrected typo or
    -- a rewritten protocol. True makes the module outstanding again for anyone whose most recent
    -- completion is older.
    supersedes_completions BOOLEAN     NOT NULL DEFAULT false,
    published_at           TIMESTAMPTZ,
    published_by           UUID REFERENCES app_user (id) ON DELETE SET NULL,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (module_id, version_number)
);

-- One draft per module. A second would need merge semantics, and nobody has asked for two
-- administrators to revise the same module at once.
CREATE UNIQUE INDEX idx_module_single_draft
    ON module_version (module_id) WHERE status = 'DRAFT';

CREATE TABLE module_section (
    id         UUID PRIMARY KEY,
    version_id UUID NOT NULL REFERENCES module_version (id) ON DELETE CASCADE,
    position   INT  NOT NULL,
    title      TEXT NOT NULL CHECK (length(trim(title)) > 0),
    -- Markdown. Never HTML: an author's content renders in colleagues' browsers, and authoring
    -- rights belong to exactly the accounts worth stealing.
    body       TEXT NOT NULL DEFAULT '',
    UNIQUE (version_id, position)
);

CREATE TABLE team_module_assignment (
    team_id     UUID        NOT NULL REFERENCES team (id) ON DELETE CASCADE,
    module_id   UUID        NOT NULL REFERENCES module (id) ON DELETE CASCADE,
    assigned_by UUID REFERENCES app_user (id) ON DELETE SET NULL,
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (team_id, module_id)
);

CREATE TABLE user_section_progress (
    user_id      UUID        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    section_id   UUID        NOT NULL REFERENCES module_section (id) ON DELETE CASCADE,
    completed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, section_id)
);

-- Pinned to the version rather than the module, and never updated: a row here is a true statement
-- about content that really existed. That is what lets "completed version 2 in June, version 3
-- outstanding since August" be expressible at all.
CREATE TABLE user_module_completion (
    user_id      UUID        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    version_id   UUID        NOT NULL REFERENCES module_version (id) ON DELETE CASCADE,
    completed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, version_id)
);

CREATE INDEX idx_module_org ON module (org_id) WHERE archived_at IS NULL;
CREATE INDEX idx_assignment_module ON team_module_assignment (module_id);
CREATE INDEX idx_section_progress_user ON user_section_progress (user_id);
