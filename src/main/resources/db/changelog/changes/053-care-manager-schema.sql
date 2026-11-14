--liquibase formatted sql

--changeset ektrepha:53
-- Care Manager feature (docs/api/openapi.yaml) — every parent and every nanny has one fixed Care
-- Manager (a `users` row with user_type CARE_MANAGER, there is no separate admin_users table in
-- this codebase). VARCHAR+CHECK for every new enum (migration 008/046 convention), not the legacy
-- SMALLINT+converter style.
--
-- cm_duty_shifts' exclusion constraint is scoped per care_manager_id (requires btree_gist for the
-- bigint equality operator class alongside the gist range operator) — a care manager's own shifts
-- must not overlap each other, but two different care managers obviously can be on duty at once.
--
-- public_id columns on parent/nanny/booking are net new here too: this feature is the first to
-- expose prefixed opaque ids (par_/nan_/bkg_) instead of raw database ids, and only Care Manager
-- payloads use them — every existing endpoint keeps returning the raw id, unchanged. Backfilled
-- deterministically from the existing primary key (hex encoding, e.g. par_1a4), so no collision
-- bookkeeping is needed; new rows get theirs assigned by the application right after insert.
--
-- access_log is meant to be append-only. The REVOKE below only has teeth when the application
-- connects as a role distinct from the table owner (Postgres never restricts an owner's own
-- privileges via REVOKE) — this repo's dev profile connects as the owning `postgres` superuser, so
-- true DB-enforced append-only needs a separate, non-owning runtime role provisioned by ops in
-- staging/prod. Until then, the real guarantee is at the application layer: AccessLogRepository
-- intentionally exposes no update/delete methods.

CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE care_managers (
    id                       BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id                VARCHAR(20)  NOT NULL UNIQUE,
    user_id                  BIGINT       NOT NULL UNIQUE REFERENCES users(id),
    name                     VARCHAR(100) NOT NULL,
    phone                    VARCHAR(15)  NOT NULL UNIQUE,
    email                    VARCHAR(255) NOT NULL UNIQUE,
    photo_url                TEXT,
    area                     VARCHAR(20)  NOT NULL CHECK (area IN ('BROOKEFIELD', 'WHITEFIELD')),
    active                   BOOLEAN      NOT NULL DEFAULT true,
    max_parents              SMALLINT     NOT NULL DEFAULT 150 CHECK (max_parents > 0),
    auto_assign_new_signups  BOOLEAN      NOT NULL DEFAULT true,
    shift_start              TIME         NOT NULL,
    shift_end                TIME         NOT NULL,
    created_at               TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_care_managers_area_active ON care_managers (area, active);

CREATE TABLE cm_assignments (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    parent_id           BIGINT REFERENCES parent(id),
    nanny_id            BIGINT REFERENCES nanny(id),
    care_manager_id     BIGINT NOT NULL REFERENCES care_managers(id),
    assigned_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    unassigned_at       TIMESTAMPTZ,
    assigned_by         BIGINT REFERENCES users(id),
    reason              VARCHAR(20) NOT NULL
        CHECK (reason IN ('SIGNUP', 'PARENT_REQUEST', 'CM_LEFT', 'ADMIN_MOVE', 'BACKFILL')),
    parent_notified_at  TIMESTAMPTZ,
    CONSTRAINT chk_cm_assignments_parent_xor_nanny CHECK ((parent_id IS NULL) <> (nanny_id IS NULL)),
    CONSTRAINT chk_cm_assignments_unassigned_after CHECK (unassigned_at IS NULL OR unassigned_at > assigned_at)
);
CREATE UNIQUE INDEX uq_cm_assignments_open_parent ON cm_assignments (parent_id) WHERE unassigned_at IS NULL AND parent_id IS NOT NULL;
CREATE UNIQUE INDEX uq_cm_assignments_open_nanny ON cm_assignments (nanny_id) WHERE unassigned_at IS NULL AND nanny_id IS NOT NULL;
CREATE INDEX idx_cm_assignments_cm_open ON cm_assignments (care_manager_id) WHERE unassigned_at IS NULL;

CREATE TABLE complaints (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id            VARCHAR(20) NOT NULL UNIQUE,
    parent_id            BIGINT NOT NULL REFERENCES parent(id),
    nanny_id             BIGINT REFERENCES nanny(id),
    booking_id           BIGINT REFERENCES booking(id),
    care_manager_id      BIGINT NOT NULL REFERENCES care_managers(id),
    category             VARCHAR(20) NOT NULL CHECK (category IN ('SAFETY', 'NANNY_CONDUCT', 'NO_SHOW', 'BILLING', 'OTHER')),
    description          TEXT NOT NULL,
    status               VARCHAR(15) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'ESCALATED', 'RESOLVED', 'CLOSED')),
    escalated_to         BIGINT REFERENCES users(id),
    escalated_at         TIMESTAMPTZ,
    trial_refund_issued  BOOLEAN NOT NULL DEFAULT false,
    resolution           TEXT,
    raised_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    closed_at            TIMESTAMPTZ,
    CONSTRAINT chk_complaints_resolution_on_close CHECK (status <> 'CLOSED' OR resolution IS NOT NULL)
);
CREATE INDEX idx_complaints_cm_status ON complaints (care_manager_id, status);
CREATE INDEX idx_complaints_nanny ON complaints (nanny_id);

CREATE TABLE cm_tasks (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id            VARCHAR(20) NOT NULL UNIQUE,
    parent_id            BIGINT NOT NULL REFERENCES parent(id),
    care_manager_id      BIGINT REFERENCES care_managers(id),
    type                 VARCHAR(30) NOT NULL CHECK (type IN (
        'SAFETY_ESCALATION', 'REPLACEMENT_NEEDED', 'LOW_RATING_CALL', 'COMPLAINT_CALL', 'PARENT_REQUEST',
        'PRE_VISIT_CALL', 'VISIT_CHECK_MESSAGE', 'FEEDBACK_REQUEST', 'INTRO_CALL', 'TRIAL_DAY4_CALL',
        'TRIAL_DAILY_CHECKIN', 'MONTHLY_CHECKIN', 'ADMIN_REASSIGN')),
    priority             VARCHAR(10) NOT NULL CHECK (priority IN ('URGENT', 'HIGH', 'NORMAL', 'LOW')),
    status               VARCHAR(10) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'DONE', 'CANCELLED')),
    title                VARCHAR(120) NOT NULL,
    detail               TEXT,
    due_at               TIMESTAMPTZ NOT NULL,
    booking_id           BIGINT REFERENCES booking(id),
    complaint_id         BIGINT REFERENCES complaints(id),
    source_event_id      UUID UNIQUE,
    note                 TEXT,
    overdue_alerted_at   TIMESTAMPTZ,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    done_at              TIMESTAMPTZ,
    done_by              BIGINT REFERENCES care_managers(id)
);
CREATE INDEX idx_cm_tasks_owner ON cm_tasks (care_manager_id, status, due_at);
CREATE INDEX idx_cm_tasks_open_due ON cm_tasks (due_at) WHERE status = 'OPEN';
CREATE INDEX idx_cm_tasks_parent ON cm_tasks (parent_id, created_at DESC);

CREATE TABLE parent_notes (
    parent_id      BIGINT PRIMARY KEY REFERENCES parent(id),
    child_routine  TEXT,
    allergies      TEXT,
    preferences    TEXT,
    expectations   TEXT,
    version        INTEGER NOT NULL DEFAULT 1,
    updated_by     BIGINT REFERENCES care_managers(id),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE cm_contacts (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id        VARCHAR(20) NOT NULL UNIQUE,
    parent_id        BIGINT NOT NULL REFERENCES parent(id),
    care_manager_id  BIGINT NOT NULL REFERENCES care_managers(id),
    channel          VARCHAR(10) NOT NULL CHECK (channel IN ('CALL', 'WHATSAPP', 'EMAIL', 'SMS')),
    direction        VARCHAR(10) NOT NULL CHECK (direction IN ('INBOUND', 'OUTBOUND')),
    source           VARCHAR(10) NOT NULL CHECK (source IN ('APP_TAP', 'CM_LOG')),
    source_screen    VARCHAR(20),
    booking_id       BIGINT REFERENCES booking(id),
    task_id          BIGINT REFERENCES cm_tasks(id),
    summary          TEXT,
    logged_by        BIGINT REFERENCES care_managers(id),
    contacted_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_cm_contacts_parent ON cm_contacts (parent_id, contacted_at DESC);

CREATE TABLE cm_duty_shifts (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id        VARCHAR(20) NOT NULL UNIQUE,
    care_manager_id  BIGINT NOT NULL REFERENCES care_managers(id),
    starts_at        TIMESTAMPTZ NOT NULL,
    ends_at          TIMESTAMPTZ NOT NULL CHECK (ends_at > starts_at),
    CONSTRAINT excl_cm_duty_shifts_no_overlap
        EXCLUDE USING gist (care_manager_id WITH =, tstzrange(starts_at, ends_at) WITH &&)
);

CREATE TABLE cm_change_requests (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id   VARCHAR(20) NOT NULL UNIQUE,
    kind        VARCHAR(10) NOT NULL CHECK (kind IN ('CM', 'NANNY')),
    parent_id   BIGINT NOT NULL REFERENCES parent(id),
    booking_id  BIGINT REFERENCES booking(id),
    reason      TEXT,
    status      VARCHAR(10) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'DONE', 'CANCELLED')),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_cm_change_requests_open ON cm_change_requests (parent_id, kind) WHERE status = 'OPEN';

CREATE TABLE access_log (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    actor_user_id  BIGINT NOT NULL REFERENCES users(id),
    record_type    VARCHAR(10) NOT NULL CHECK (record_type IN ('PARENT', 'NANNY')),
    record_id      BIGINT NOT NULL,
    action         VARCHAR(10) NOT NULL CHECK (action IN ('VIEW', 'EDIT')),
    section        VARCHAR(30),
    at             TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_access_log_record ON access_log (record_type, record_id, at DESC);
CREATE INDEX idx_access_log_actor ON access_log (actor_user_id, at DESC);
REVOKE UPDATE, DELETE ON access_log FROM PUBLIC;

CREATE TABLE outbox_events (
    event_id      UUID PRIMARY KEY,
    type          VARCHAR(50) NOT NULL,
    payload       JSONB NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    processed_at  TIMESTAMPTZ,
    attempts      INTEGER NOT NULL DEFAULT 0,
    last_error    TEXT
);
CREATE INDEX idx_outbox_events_unprocessed ON outbox_events (created_at) WHERE processed_at IS NULL;

CREATE TABLE idempotency_keys (
    key           VARCHAR(64) NOT NULL,
    user_id       BIGINT NOT NULL REFERENCES users(id),
    request_hash  CHAR(64) NOT NULL,
    status_code   SMALLINT NOT NULL,
    response      JSONB NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (key, user_id)
);

-- Opaque public ids, backfilled deterministically from the existing primary key.
ALTER TABLE parent ADD COLUMN public_id VARCHAR(20);
ALTER TABLE nanny ADD COLUMN public_id VARCHAR(20);
ALTER TABLE booking ADD COLUMN public_id VARCHAR(20);
UPDATE parent SET public_id = 'par_' || to_hex(id) WHERE public_id IS NULL;
UPDATE nanny SET public_id = 'nan_' || to_hex(id) WHERE public_id IS NULL;
UPDATE booking SET public_id = 'bkg_' || to_hex(id) WHERE public_id IS NULL;
ALTER TABLE parent ALTER COLUMN public_id SET NOT NULL;
ALTER TABLE nanny ALTER COLUMN public_id SET NOT NULL;
ALTER TABLE booking ALTER COLUMN public_id SET NOT NULL;
ALTER TABLE parent ADD CONSTRAINT uq_parent_public_id UNIQUE (public_id);
ALTER TABLE nanny ADD CONSTRAINT uq_nanny_public_id UNIQUE (public_id);
ALTER TABLE booking ADD CONSTRAINT uq_booking_public_id UNIQUE (public_id);

-- Nullable now (per spec); backfilled by a dry-run-capable job, NOT NULL only in a later migration
-- once the backfill is verified.
ALTER TABLE parent ADD COLUMN care_manager_id BIGINT REFERENCES care_managers(id);
CREATE INDEX idx_parent_care_manager_id ON parent (care_manager_id);
ALTER TABLE nanny ADD COLUMN care_manager_id BIGINT REFERENCES care_managers(id);
CREATE INDEX idx_nanny_care_manager_id ON nanny (care_manager_id);

-- Operational status (flag/suspend/leave), distinct from nanny.overall_verification_status (the
-- KYC/onboarding rollup in NannyVerificationStatus) — different concern, different lifecycle.
ALTER TABLE nanny ADD COLUMN status VARCHAR(15) NOT NULL DEFAULT 'ACTIVE'
    CHECK (status IN ('ACTIVE', 'FLAGGED', 'SUSPENDED', 'ON_LEAVE'));

-- Name/phone search (GET /cm/parents?q=, GET /cm/nannies?q=).
CREATE INDEX idx_parent_name_trgm ON parent USING gin ((coalesce(first_name, '') || ' ' || coalesce(last_name, '')) gin_trgm_ops);
CREATE INDEX idx_nanny_name_trgm ON nanny USING gin ((first_name || ' ' || last_name) gin_trgm_ops);
CREATE INDEX idx_users_phone_trgm ON users USING gin (phone gin_trgm_ops);
