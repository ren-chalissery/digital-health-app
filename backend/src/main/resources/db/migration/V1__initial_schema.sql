-- Phase 1: organisations, users, teams, invitations, and the audit trail.
--
-- Enum-like columns are stored as TEXT with CHECK constraints rather than native Postgres enums,
-- because adding a value to a native enum cannot run inside a transaction and complicates Flyway
-- migrations. The application maps them to Java enums.

CREATE EXTENSION IF NOT EXISTS citext;

CREATE TABLE organisation (
    id                UUID PRIMARY KEY,
    name              TEXT        NOT NULL CHECK (length(trim(name)) > 0),
    slug              TEXT        NOT NULL UNIQUE CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
    organisation_type TEXT        NOT NULL CHECK (organisation_type IN
                                                  ('HOSPITAL', 'CLINIC', 'UNIVERSITY', 'COMPANY', 'OTHER')),
    country           TEXT,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- "user" is reserved in Postgres.
CREATE TABLE app_user (
    id                UUID PRIMARY KEY,
    -- The only column coupling this schema to Cognito. Nullable so an administrator can invite
    -- somebody who has not yet created their Cognito account.
    cognito_sub       TEXT UNIQUE,
    email             CITEXT      NOT NULL UNIQUE,
    full_name         TEXT,
    phone             TEXT,
    professional_role TEXT,
    platform_role     TEXT        NOT NULL DEFAULT 'STANDARD' CHECK (platform_role IN ('SUPER_ADMIN', 'STANDARD')),
    status            TEXT        NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INVITED', 'DEACTIVATED')),
    profile_completed BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_app_user_cognito_sub ON app_user (cognito_sub);

-- A join table rather than a column on app_user, so a clinician working across two hospitals has
-- one account. Retrofitting this later would mean migrating live membership data.
CREATE TABLE org_membership (
    user_id   UUID        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    org_id    UUID        NOT NULL REFERENCES organisation (id) ON DELETE CASCADE,
    org_role  TEXT        NOT NULL CHECK (org_role IN ('ORG_ADMIN', 'ORG_MEMBER')),
    status    TEXT        NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'SUSPENDED')),
    joined_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, org_id)
);

CREATE INDEX idx_org_membership_org ON org_membership (org_id);

CREATE TABLE team (
    id          UUID PRIMARY KEY,
    org_id      UUID        NOT NULL REFERENCES organisation (id) ON DELETE CASCADE,
    name        TEXT        NOT NULL CHECK (length(trim(name)) > 0),
    description TEXT,
    created_by  UUID REFERENCES app_user (id) ON DELETE SET NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (org_id, name)
);

CREATE INDEX idx_team_org ON team (org_id);

CREATE TABLE team_member (
    team_id   UUID        NOT NULL REFERENCES team (id) ON DELETE CASCADE,
    user_id   UUID        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    team_role TEXT        NOT NULL CHECK (team_role IN ('TEAM_ADMIN', 'TEAM_MEMBER')),
    joined_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (team_id, user_id)
);

CREATE INDEX idx_team_member_user ON team_member (user_id);

CREATE TABLE invitation (
    id          UUID PRIMARY KEY,
    org_id      UUID        NOT NULL REFERENCES organisation (id) ON DELETE CASCADE,
    team_id     UUID REFERENCES team (id) ON DELETE SET NULL,
    email       CITEXT      NOT NULL,
    org_role    TEXT        NOT NULL CHECK (org_role IN ('ORG_ADMIN', 'ORG_MEMBER')),
    team_role   TEXT CHECK (team_role IN ('TEAM_ADMIN', 'TEAM_MEMBER')),
    -- SHA-256 of the token that was emailed. The token itself is never persisted, so a database
    -- leak cannot be replayed into account access.
    token_hash  TEXT        NOT NULL UNIQUE,
    status      TEXT        NOT NULL DEFAULT 'PENDING' CHECK (status IN
                                                              ('PENDING', 'ACCEPTED', 'REVOKED', 'EXPIRED')),
    invited_by  UUID REFERENCES app_user (id) ON DELETE SET NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    accepted_at TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT invitation_team_role_requires_team CHECK (team_role IS NULL OR team_id IS NOT NULL)
);

-- Makes re-invitation idempotent at the database level: a second pending invitation for the same
-- address in the same organisation cannot exist, whatever the service layer does.
CREATE UNIQUE INDEX idx_invitation_one_pending_per_email
    ON invitation (org_id, email) WHERE status = 'PENDING';

CREATE INDEX idx_invitation_org ON invitation (org_id);

CREATE TABLE audit_event (
    id            UUID PRIMARY KEY,
    actor_user_id UUID REFERENCES app_user (id) ON DELETE SET NULL,
    org_id        UUID REFERENCES organisation (id) ON DELETE CASCADE,
    action        TEXT        NOT NULL,
    target_type   TEXT,
    target_id     TEXT,
    metadata      JSONB,
    ip_address    TEXT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_event_org_created ON audit_event (org_id, created_at DESC);
