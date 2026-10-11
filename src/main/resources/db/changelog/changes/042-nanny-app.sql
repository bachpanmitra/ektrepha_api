--liquibase formatted sql

--changeset ektrepha:42
-- Everything the nanny staff app (ektrepha-nanny-ui) needs that had no backing table yet. The
-- app's "shift" is a booking (one row per occurrence), so shift-scoped data hangs off booking_id.

-- 1:1 HR/employment record for an in-house nanny. Kept out of `nanny` itself (the marketplace
-- profile) since none of this is visible to parents. employment_status NULL means "derive it from
-- nanny.overall_verification_status" - so every already-approved nanny works in the app without a
-- backfill.
CREATE TABLE nanny_employment (
    nanny_id                BIGINT PRIMARY KEY REFERENCES nanny(id) ON DELETE CASCADE,
    employee_code           VARCHAR(20) UNIQUE,
    employment_status       VARCHAR(20) CHECK (employment_status IN ('ONBOARDING', 'ACTIVE', 'SUSPENDED', 'EXITED')),
    start_date              DATE,
    app_language            VARCHAR(5) NOT NULL DEFAULT 'en' CHECK (app_language IN ('en', 'kn', 'hi')),
    manager_name            VARCHAR(100),
    manager_phone           VARCHAR(20),
    base_monthly_salary     NUMERIC(10, 2),
    bank_account_number     VARCHAR(30),
    bank_ifsc               VARCHAR(11),
    bank_account_holder     VARCHAR(150),
    bank_verified           BOOLEAN NOT NULL DEFAULT false,
    pf_uan                  VARCHAR(20),
    esi_number              VARCHAR(20),
    emergency_name          VARCHAR(100),
    emergency_relation      VARCHAR(50),
    emergency_phone         VARCHAR(20),
    offer_letter_signed_at  TIMESTAMPTZ,
    uniform_issued_at       TIMESTAMPTZ,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- In-person induction/refresher sessions HR schedules (distinct from the online
-- nanny_training_attempt quiz).
CREATE TABLE nanny_training_session (
    id             BIGSERIAL PRIMARY KEY,
    nanny_id       BIGINT NOT NULL REFERENCES nanny(id) ON DELETE CASCADE,
    title          VARCHAR(150) NOT NULL,
    start_date     DATE NOT NULL,
    location       VARCHAR(200) NOT NULL,
    duration_days  INT NOT NULL DEFAULT 1 CHECK (duration_days > 0),
    completed      BOOLEAN NOT NULL DEFAULT false,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_nanny_training_session_nanny_id ON nanny_training_session(nanny_id);

-- "On my way" tap - the step before check-in.
ALTER TABLE booking ADD COLUMN on_the_way_at TIMESTAMPTZ;

CREATE TABLE nanny_shift_update (
    id          BIGSERIAL PRIMARY KEY,
    booking_id  BIGINT NOT NULL REFERENCES booking(id),
    nanny_id    BIGINT NOT NULL REFERENCES nanny(id),
    text        VARCHAR(1000) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_nanny_shift_update_booking_id ON nanny_shift_update(booking_id);

-- At most one extension request per booking; the family accepts/declines it.
CREATE TABLE nanny_shift_extension (
    id                 BIGSERIAL PRIMARY KEY,
    booking_id         BIGINT NOT NULL UNIQUE REFERENCES booking(id),
    nanny_id           BIGINT NOT NULL REFERENCES nanny(id),
    requested_minutes  INT NOT NULL CHECK (requested_minutes > 0),
    status             VARCHAR(20) NOT NULL DEFAULT 'PENDING_FAMILY' CHECK (status IN ('PENDING_FAMILY', 'ACCEPTED', 'DECLINED')),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- The nanny's private rating of the family after a shift (never shown to the parent).
CREATE TABLE nanny_family_review (
    id            BIGSERIAL PRIMARY KEY,
    booking_id    BIGINT NOT NULL UNIQUE REFERENCES booking(id),
    nanny_id      BIGINT NOT NULL REFERENCES nanny(id),
    parent_id     BIGINT NOT NULL REFERENCES parent(id),
    stars         SMALLINT NOT NULL CHECK (stars BETWEEN 1 AND 5),
    tags          VARCHAR(200),
    private_note  VARCHAR(1000),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_nanny_family_review_parent_id ON nanny_family_review(parent_id);

-- "Can't come today" - same-day notice for one shift, separate from planned leave_request.
CREATE TABLE nanny_absence (
    id          BIGSERIAL PRIMARY KEY,
    booking_id  BIGINT NOT NULL REFERENCES booking(id),
    nanny_id    BIGINT NOT NULL REFERENCES nanny(id),
    reason      VARCHAR(20) NOT NULL CHECK (reason IN ('UNWELL', 'FAMILY_EMERGENCY', 'RUNNING_LATE', 'TRAVEL_PROBLEM')),
    status      VARCHAR(20) NOT NULL DEFAULT 'SENT' CHECK (status IN ('SENT', 'ACKNOWLEDGED')),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_nanny_absence_booking_id ON nanny_absence(booking_id);

ALTER TABLE leave_request ADD COLUMN leave_type VARCHAR(10) CHECK (leave_type IN ('CASUAL', 'SICK', 'EARNED'));
ALTER TABLE shift_change_request ADD COLUMN kind VARCHAR(20) CHECK (kind IN ('SWAP', 'RESCHEDULE', 'LEAVE_FOR_SHIFT'));
ALTER TABLE sos_alert ADD COLUMN cancelled_at TIMESTAMPTZ;
ALTER TABLE incident_report ADD COLUMN category VARCHAR(40);
ALTER TABLE incident_report ADD COLUMN request_callback BOOLEAN NOT NULL DEFAULT false;

CREATE TABLE nanny_payslip (
    id          BIGSERIAL PRIMARY KEY,
    nanny_id    BIGINT NOT NULL REFERENCES nanny(id) ON DELETE CASCADE,
    month       VARCHAR(7) NOT NULL,
    amount      NUMERIC(10, 2) NOT NULL,
    paid_date   DATE NOT NULL,
    s3_key      VARCHAR(500),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (nanny_id, month)
);
COMMENT ON COLUMN nanny_payslip.month IS 'YYYY-MM';

-- In-app notification inbox (the nanny app's Updates screen). Role-agnostic, keyed by user.
CREATE TABLE user_notification (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title               VARCHAR(200) NOT NULL,
    body                VARCHAR(500) NOT NULL DEFAULT '',
    deep_link_route     VARCHAR(50),
    deep_link_shift_id  VARCHAR(50),
    read_at             TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_user_notification_user_id ON user_notification(user_id, created_at DESC);
