-- Retrieval over training content, in the database that already exists. Phase 1 flagged Neptune at
-- several times the cost of this whole stack and said to measure pgvector first; at an
-- organisation's scale of thousands of chunks, the measurement is not close.
CREATE EXTENSION IF NOT EXISTS vector;

-- Null until the scheduled indexer has embedded this version. Publishing does not wait for it, so
-- an administrator can still publish while Bedrock is unavailable.
ALTER TABLE module_version
    ADD COLUMN indexed_at TIMESTAMPTZ;

CREATE TABLE module_chunk (
    id            UUID PRIMARY KEY,
    org_id        UUID         NOT NULL REFERENCES organisation (id) ON DELETE CASCADE,
    module_id     UUID         NOT NULL REFERENCES module (id) ON DELETE CASCADE,
    -- Chunks hang off a version, which is immutable once published, so a chunk can never describe
    -- content that has since been rewritten.
    version_id    UUID         NOT NULL REFERENCES module_version (id) ON DELETE CASCADE,
    section_id    UUID REFERENCES module_section (id) ON DELETE SET NULL,
    module_title  TEXT         NOT NULL,
    section_title TEXT,
    content       TEXT         NOT NULL,
    -- 1024 is the Titan embed text v2 default.
    embedding     VECTOR(1024) NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_module_chunk_embedding ON module_chunk USING hnsw (embedding vector_cosine_ops);
CREATE INDEX idx_module_chunk_org ON module_chunk (org_id);
CREATE INDEX idx_module_version_unindexed
    ON module_version (id) WHERE status = 'PUBLISHED' AND indexed_at IS NULL;
