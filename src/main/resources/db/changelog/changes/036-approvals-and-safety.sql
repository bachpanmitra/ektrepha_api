--liquibase formatted sql

--changeset ektrepha:36
-- Admin portal Phase 5 (Approvals & Safety) — none of these concepts had any backing table before
-- this. Each request/alert table is deliberately narrow (no structured attendance record to
-- "correct" exists yet — attendance_correction_request.details is free text rather than old/new
-- check-in timestamps, since there's nothing to diff against until the Roster/Attendance phase
-- lands). Approving a shift_change_request is handled at the application layer (not a DB trigger):
-- it also flips the booking back to ASSIGNING_CAREGIVER so it reappears in the existing Assign
-- screen, per the admin spec's "an approval that frees a shift links straight to its Assign screen".

CREATE TABLE leave_request (
    id                BIGSERIAL PRIMARY KEY,
    nanny_id          BIGINT NOT NULL REFERENCES nanny(id),
    start_date        DATE NOT NULL,
    end_date          DATE NOT NULL,
    reason            VARCHAR(500) NOT NULL,
    status            SMALLINT NOT NULL DEFAULT 1,
    reviewed_by       BIGINT REFERENCES users(id),
    reviewed_at       TIMESTAMPTZ,
    rejection_reason  VARCHAR(255),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT leave_request_dates_check CHECK (end_date >= start_date)
);
COMMENT ON COLUMN leave_request.status IS '1=PENDING, 2=APPROVED, 3=REJECTED';
CREATE INDEX idx_leave_request_nanny_id ON leave_request(nanny_id);
CREATE INDEX idx_leave_request_status ON leave_request(status);

CREATE TABLE shift_change_request (
    id                BIGSERIAL PRIMARY KEY,
    booking_id        BIGINT NOT NULL REFERENCES booking(id),
    nanny_id          BIGINT NOT NULL REFERENCES nanny(id),
    reason            VARCHAR(500) NOT NULL,
    status            SMALLINT NOT NULL DEFAULT 1,
    reviewed_by       BIGINT REFERENCES users(id),
    reviewed_at       TIMESTAMPTZ,
    rejection_reason  VARCHAR(255),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
COMMENT ON COLUMN shift_change_request.status IS '1=PENDING, 2=APPROVED, 3=REJECTED';
CREATE INDEX idx_shift_change_request_booking_id ON shift_change_request(booking_id);
CREATE INDEX idx_shift_change_request_nanny_id ON shift_change_request(nanny_id);

CREATE TABLE attendance_correction_request (
    id                BIGSERIAL PRIMARY KEY,
    booking_id        BIGINT NOT NULL REFERENCES booking(id),
    nanny_id          BIGINT NOT NULL REFERENCES nanny(id),
    details           VARCHAR(500) NOT NULL,
    status            SMALLINT NOT NULL DEFAULT 1,
    reviewed_by       BIGINT REFERENCES users(id),
    reviewed_at       TIMESTAMPTZ,
    rejection_reason  VARCHAR(255),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
COMMENT ON COLUMN attendance_correction_request.status IS '1=PENDING, 2=APPROVED, 3=REJECTED';
COMMENT ON COLUMN attendance_correction_request.details IS 'Free-text description of the requested correction - there is no structured check-in/check-out record yet to correct against (see Attendance phase)';
CREATE INDEX idx_attendance_correction_request_booking_id ON attendance_correction_request(booking_id);

CREATE TABLE sos_alert (
    id                BIGSERIAL PRIMARY KEY,
    booking_id        BIGINT REFERENCES booking(id),
    nanny_id          BIGINT NOT NULL REFERENCES nanny(id),
    lat               DOUBLE PRECISION,
    lng               DOUBLE PRECISION,
    notes             VARCHAR(500),
    acknowledged_at   TIMESTAMPTZ,
    acknowledged_by   BIGINT REFERENCES users(id),
    resolved_at       TIMESTAMPTZ,
    resolved_by       BIGINT REFERENCES users(id),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_sos_alert_nanny_id ON sos_alert(nanny_id);
-- Partial index for the open-SOS-count dashboard query — only unresolved rows are ever scanned by it.
CREATE INDEX idx_sos_alert_unresolved ON sos_alert(created_at) WHERE resolved_at IS NULL;

CREATE TABLE incident_report (
    id                BIGSERIAL PRIMARY KEY,
    booking_id        BIGINT REFERENCES booking(id),
    nanny_id          BIGINT REFERENCES nanny(id),
    reported_by       BIGINT NOT NULL REFERENCES users(id),
    description       VARCHAR(2000) NOT NULL,
    status            SMALLINT NOT NULL DEFAULT 1,
    resolved_by       BIGINT REFERENCES users(id),
    resolved_at       TIMESTAMPTZ,
    resolution_notes  VARCHAR(1000),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
COMMENT ON COLUMN incident_report.status IS '1=OPEN, 2=RESOLVED';
CREATE INDEX idx_incident_report_status ON incident_report(status);
