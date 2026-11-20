--liquibase formatted sql

--changeset ektrepha:57
-- Backs the nanny app's move off mock data for: profile (NannyMe), personal emergency contact,
-- post-stay review, a free-text duty update log, and a notifications inbox. See
-- ektrepha-nanny-ui/config.ts's USE_MOCK_API comment for the full list of what this does and
-- does not cover (medicine/routine schedules and onboarding steps are deliberately excluded —
-- no authoring data model exists for either yet).

-- Profile language preference (app language picker) and personal emergency contact — simple
-- scalar fields directly on nanny, same pattern as bio/dob/device_id already there.
ALTER TABLE nanny ADD COLUMN language VARCHAR(5) NOT NULL DEFAULT 'en';
ALTER TABLE nanny ADD COLUMN emergency_contact_name VARCHAR(100);
ALTER TABLE nanny ADD COLUMN emergency_contact_relation VARCHAR(50);
ALTER TABLE nanny ADD COLUMN emergency_contact_phone VARCHAR(15);

-- The nanny's own rating/feedback on a completed stay — the reverse direction of `review`
-- (parent rates nanny). One per booking, never edited after submission.
CREATE TABLE nanny_stay_review (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    booking_id BIGINT NOT NULL UNIQUE REFERENCES booking(id),
    nanny_id BIGINT NOT NULL REFERENCES nanny(id),
    stars SMALLINT NOT NULL CHECK (stars BETWEEN 1 AND 5),
    tags TEXT[] NOT NULL DEFAULT '{}',
    private_note VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- A free-text note the nanny can log during a shift (e.g. "started lunch") — append-only, no
-- edit/delete path, matching the app's single addDutyUpdate action (no edit UI either).
CREATE TABLE booking_duty_update (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    booking_id BIGINT NOT NULL REFERENCES booking(id),
    nanny_id BIGINT NOT NULL REFERENCES nanny(id),
    note VARCHAR(1000) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_booking_duty_update_booking ON booking_duty_update (booking_id);

-- In-app notification inbox (GET/mark-read, the nanny app's "Updates" screen). Nothing writes to
-- this table yet in this change — every existing notifier (CM, trial, SOS, workforce requests) is
-- push-only/fire-and-forget today; wiring them to also insert here is deliberately left for a
-- follow-up rather than bundled into this schema change.
CREATE TABLE notification (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    title VARCHAR(200) NOT NULL,
    body VARCHAR(500),
    route VARCHAR(50),
    booking_id BIGINT REFERENCES booking(id),
    read_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_notification_user ON notification (user_id, created_at DESC);
