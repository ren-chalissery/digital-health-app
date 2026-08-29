-- Archiving rather than deletion. audit_event.org_id is ON DELETE CASCADE, so removing an
-- organisation row would silently take its entire history with it, and that history is the point:
-- it is what answers who removed whom, and when.
-- Both references are ON DELETE SET NULL, matching audit_event.actor_user_id. The two tables now
-- point at each other, and without it neither row could ever be deleted: an organisation would be
-- pinned by whoever archived it, and a user by whichever organisation they last looked at. Who
-- archived something is worth recording, not worth blocking a deletion for.
ALTER TABLE organisation
    ADD COLUMN archived_at TIMESTAMPTZ,
    ADD COLUMN archived_by UUID REFERENCES app_user (id) ON DELETE SET NULL;

-- Which organisation a clinician is currently looking at. A preference, never an authorisation
-- input: membership is checked on every request regardless of what this says.
ALTER TABLE app_user
    ADD COLUMN active_org_id UUID REFERENCES organisation (id) ON DELETE SET NULL;

-- No index accompanies either column. Archived organisations are filtered while building a
-- principal, which reaches them by primary key through a membership the user already has, and this
-- table holds a handful of rows.
