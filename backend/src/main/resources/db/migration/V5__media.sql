-- Video. An asset belongs to the organisation rather than to a section, so opening a draft copies
-- a reference instead of an S3 object and deleting a version needs no reference counting.

CREATE TABLE media_asset (
    id               UUID PRIMARY KEY,
    org_id           UUID        NOT NULL REFERENCES organisation (id) ON DELETE CASCADE,
    filename         TEXT        NOT NULL,
    content_type     TEXT        NOT NULL,
    size_bytes       BIGINT,
    status           TEXT        NOT NULL CHECK (status IN ('UPLOADING', 'PROCESSING', 'READY', 'FAILED')),
    -- Whatever MediaConvert said, kept verbatim. A video that silently does nothing reads as a
    -- broken product; a video that says why it failed reads as one that is working.
    failure_reason   TEXT,
    upload_key       TEXT        NOT NULL,
    playback_key     TEXT,
    duration_seconds INT,
    transcode_job_id TEXT,
    created_by       UUID REFERENCES app_user (id) ON DELETE SET NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- SET NULL rather than CASCADE: deleting a video should empty the sections that used it, not
-- delete the writing around it.
ALTER TABLE module_section
    ADD COLUMN media_asset_id UUID REFERENCES media_asset (id) ON DELETE SET NULL;

CREATE INDEX idx_media_asset_org ON media_asset (org_id);

-- The poller asks only this question, twice a minute.
CREATE INDEX idx_media_asset_processing ON media_asset (status) WHERE status = 'PROCESSING';
