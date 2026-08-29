-- A quiz belongs to a module version, exactly as sections do, so publishing freezes it and an
-- attempt can pin to the content it was actually taken against.

CREATE TABLE quiz_question (
    id          UUID PRIMARY KEY,
    version_id  UUID NOT NULL REFERENCES module_version (id) ON DELETE CASCADE,
    position    INT  NOT NULL,
    prompt      TEXT NOT NULL CHECK (length(trim(prompt)) > 0),
    -- Shown after an attempt. Retakes are unlimited and feedback is full, so without this the
    -- feedback is an answer key and retrying is mechanical; this is where it becomes teaching.
    explanation TEXT,
    UNIQUE (version_id, position)
);

CREATE TABLE quiz_option (
    id          UUID PRIMARY KEY,
    question_id UUID    NOT NULL REFERENCES quiz_question (id) ON DELETE CASCADE,
    position    INT     NOT NULL,
    label       TEXT    NOT NULL CHECK (length(trim(label)) > 0),
    correct     BOOLEAN NOT NULL DEFAULT false,
    UNIQUE (question_id, position)
);

-- Every attempt, not only the passing one. "Passed on the fourth attempt" is a different fact from
-- "passed" and cannot be reconstructed later. Nothing reads this yet.
CREATE TABLE quiz_attempt (
    id             UUID PRIMARY KEY,
    user_id        UUID        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    version_id     UUID        NOT NULL REFERENCES module_version (id) ON DELETE CASCADE,
    attempt_number INT         NOT NULL,
    correct_count  INT         NOT NULL,
    question_count INT         NOT NULL,
    passed         BOOLEAN     NOT NULL,
    submitted_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, version_id, attempt_number)
);

CREATE INDEX idx_quiz_attempt_user_version ON quiz_attempt (user_id, version_id);
