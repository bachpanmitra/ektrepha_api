--liquibase formatted sql

--changeset ektrepha:50
-- Nanny consent before the app collects documents or live location (nanny app spec: "Consent
-- before upload and location", API Map row 39). One row per (nanny, type); re-granting just
-- bumps granted_at.
CREATE TABLE nanny_consent (
    id BIGSERIAL PRIMARY KEY,
    nanny_id BIGINT NOT NULL REFERENCES nanny(id),
    type SMALLINT NOT NULL,
    granted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (nanny_id, type)
);

-- Dedup flags for the two attendance-alert scheduler sweeps (API Map rows 42, 43) — each sweep
-- runs every few minutes and must never re-alert for the same booking_attendance row once sent.
ALTER TABLE booking_attendance ADD COLUMN late_check_in_alert_sent_at TIMESTAMPTZ;
ALTER TABLE booking_attendance ADD COLUMN forgot_check_out_alert_sent_at TIMESTAMPTZ;
