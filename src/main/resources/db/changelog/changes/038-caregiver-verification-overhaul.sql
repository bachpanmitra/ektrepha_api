--liquibase formatted sql

--changeset ektrepha:38
-- Caregiver verification overhaul (child-safety program, phase 1): the old 4-state rollup
-- (PENDING/PARTIAL/VERIFIED/REJECTED) only ever reflected three self-uploaded documents
-- (ID_PROOF/BACKGROUND_CHECK/EDUCATION). This changeset adds everything the rollup needs to
-- gate on instead: age-18 enforcement, an address-proof and liveness-selfie document type, a
-- PCC expiry date (so a lapsed police clearance can auto-suspend a caregiver), structured
-- references (min 2, independently verified) instead of one REFERENCE document slot, a
-- recorded video-interview outcome, a training/quiz attempt, a signed code-of-conduct
-- acceptance, a full status-change audit trail, and a ban-evasion lookup table.
--
-- PARTIAL/VERIFIED are renamed UNDER_REVIEW/APPROVED (same codes 2/3 - no data migration
-- needed) and two new terminal-ish states are added: SUSPENDED(5, reversible - e.g. an expired
-- document) and BANNED(6, not reversible via the normal recompute path). Nanny.java's recompute
-- guard (see VerificationRollupCalculator) never auto-overwrites SUSPENDED/BANNED - those are
-- admin/scheduled-job-only transitions.

ALTER TABLE nanny
    ADD COLUMN dob DATE,
    ADD COLUMN status_reason VARCHAR(500),
    ADD COLUMN status_changed_by BIGINT REFERENCES users(id),
    ADD COLUMN status_changed_at TIMESTAMPTZ,
    ADD COLUMN device_id VARCHAR(128);
COMMENT ON COLUMN nanny.dob IS 'Nullable only for rows created before this changeset; every new nanny must supply it (18+ enforced at the application layer and by the check constraint below).';
ALTER TABLE nanny ADD CONSTRAINT nanny_dob_18_plus_check CHECK (dob IS NULL OR dob <= (now() - INTERVAL '18 years'));

ALTER TABLE nanny DROP CONSTRAINT nanny_overall_verification_status_check;
ALTER TABLE nanny ADD CONSTRAINT nanny_overall_verification_status_check CHECK (overall_verification_status IN (1, 2, 3, 4, 5, 6));
COMMENT ON COLUMN nanny.overall_verification_status IS '1=PENDING, 2=UNDER_REVIEW, 3=APPROVED, 4=REJECTED, 5=SUSPENDED, 6=BANNED';

CREATE INDEX idx_nanny_device_id ON nanny(device_id) WHERE device_id IS NOT NULL;

-- PCC (police clearance certificate) is submitted as a BACKGROUND_CHECK document; expiry_date
-- is only ever set for that type, but kept generic (not a separate pcc_expiry_date column) since
-- any future doc type could gain an expiry without another migration.
ALTER TABLE nanny_verification ADD COLUMN expiry_date DATE;

-- One-way hashes from KycVerificationProvider (never the raw extracted ID number or a reusable
-- biometric template) - id_doc_hash is only ever set on an ID_PROOF row, face_embedding_hash only
-- on a LIVENESS_SELFIE row. Stored here (not just checked-and-discarded at submission time) so
-- that banning a nanny can seed banned_identity with their actual hashes for future ban-evasion
-- matching - see BanEvasionCheckService.
ALTER TABLE nanny_verification
    ADD COLUMN id_doc_hash VARCHAR(128),
    ADD COLUMN face_embedding_hash VARCHAR(128);

ALTER TABLE nanny_verification DROP CONSTRAINT nanny_verification_type_check;
ALTER TABLE nanny_verification ADD CONSTRAINT nanny_verification_type_check CHECK (type IN (1, 2, 3, 4, 5, 6, 7));
COMMENT ON COLUMN nanny_verification.type IS '1=ID_PROOF, 2=BACKGROUND_CHECK (PCC), 3=EDUCATION, 4=FIRST_AID, 5=REFERENCE (legacy, superseded by nanny_reference), 6=ADDRESS_PROOF, 7=LIVENESS_SELFIE';

-- Index for the expiry-audit job: only VERIFIED BACKGROUND_CHECK rows with a known expiry are
-- ever scanned by it, so a partial index keeps that scan cheap regardless of table size.
CREATE INDEX idx_nanny_verification_pcc_expiry ON nanny_verification(expiry_date)
    WHERE type = 2 AND status = 2 AND expiry_date IS NOT NULL;

-- Structured references: spec requires a minimum of 2, each independently verified - a single
-- REFERENCE document slot (migration 006) can't represent that, so this replaces it going
-- forward (the old REFERENCE doc type stays defined above for any historical rows).
CREATE TABLE nanny_reference (
    id               BIGSERIAL PRIMARY KEY,
    nanny_id         BIGINT NOT NULL REFERENCES nanny(id) ON DELETE CASCADE,
    name             VARCHAR(150) NOT NULL,
    phone            VARCHAR(20) NOT NULL,
    relationship     VARCHAR(100),
    status           SMALLINT NOT NULL DEFAULT 1 CHECK (status IN (1, 2, 3)),
    verified_by      BIGINT REFERENCES users(id),
    verified_at      TIMESTAMPTZ,
    rejection_reason VARCHAR(255),
    notes            VARCHAR(500),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
COMMENT ON COLUMN nanny_reference.status IS '1=PENDING, 2=VERIFIED, 3=REJECTED';
CREATE INDEX idx_nanny_reference_nanny_id ON nanny_reference(nanny_id);

-- One row per scheduled/conducted interview. A nanny can be re-interviewed (e.g. after
-- NEEDS_FOLLOWUP), so no uniqueness constraint on nanny_id - same "latest row wins" shape as
-- nanny_verification.
CREATE TABLE nanny_interview (
    id             BIGSERIAL PRIMARY KEY,
    nanny_id       BIGINT NOT NULL REFERENCES nanny(id) ON DELETE CASCADE,
    scheduled_at   TIMESTAMPTZ NOT NULL,
    conducted_by   BIGINT REFERENCES users(id),
    conducted_at   TIMESTAMPTZ,
    outcome        SMALLINT NOT NULL DEFAULT 1 CHECK (outcome IN (1, 2, 3, 4)),
    notes          VARCHAR(1000),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
COMMENT ON COLUMN nanny_interview.outcome IS '1=SCHEDULED, 2=PASSED, 3=FAILED, 4=NEEDS_FOLLOWUP';
CREATE INDEX idx_nanny_interview_nanny_id ON nanny_interview(nanny_id);

-- Every quiz submission is kept (not just the latest) - a failed attempt followed by a retake is
-- part of the training record, not something to overwrite.
CREATE TABLE nanny_training_attempt (
    id             BIGSERIAL PRIMARY KEY,
    nanny_id       BIGINT NOT NULL REFERENCES nanny(id) ON DELETE CASCADE,
    module_version VARCHAR(20) NOT NULL,
    score          INT NOT NULL,
    passed         BOOLEAN NOT NULL,
    started_at     TIMESTAMPTZ NOT NULL,
    completed_at   TIMESTAMPTZ NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_nanny_training_attempt_nanny_id ON nanny_training_attempt(nanny_id, created_at DESC);

-- Every acceptance is kept (not just the latest) - re-acceptance after a version bump must not
-- erase the record that the nanny accepted an earlier version on an earlier date.
CREATE TABLE nanny_code_of_conduct_acceptance (
    id          BIGSERIAL PRIMARY KEY,
    nanny_id    BIGINT NOT NULL REFERENCES nanny(id) ON DELETE CASCADE,
    version     VARCHAR(20) NOT NULL,
    accepted_at TIMESTAMPTZ NOT NULL,
    ip_address  VARCHAR(45),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_nanny_coc_acceptance_nanny_id ON nanny_code_of_conduct_acceptance(nanny_id, created_at DESC);

-- Ban-evasion lookup: every hash column is nullable (not every signal is available for every
-- nanny) and independently indexed, so a signup-time check can match on whichever signals it has
-- (phone is already covered by users.phone's own UNIQUE constraint; this table exists for the
-- signals that constraint can't cover - ID document, device, bank account, face).
CREATE TABLE banned_identity (
    id                 BIGSERIAL PRIMARY KEY,
    phone_hash         VARCHAR(128),
    id_doc_hash        VARCHAR(128),
    device_id          VARCHAR(128),
    bank_account_hash  VARCHAR(128),
    face_embedding_hash VARCHAR(128),
    banned_nanny_id    BIGINT REFERENCES nanny(id),
    reason             VARCHAR(500) NOT NULL,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_banned_identity_phone_hash ON banned_identity(phone_hash) WHERE phone_hash IS NOT NULL;
CREATE INDEX idx_banned_identity_id_doc_hash ON banned_identity(id_doc_hash) WHERE id_doc_hash IS NOT NULL;
CREATE INDEX idx_banned_identity_device_id ON banned_identity(device_id) WHERE device_id IS NOT NULL;
CREATE INDEX idx_banned_identity_bank_account_hash ON banned_identity(bank_account_hash) WHERE bank_account_hash IS NOT NULL;
CREATE INDEX idx_banned_identity_face_hash ON banned_identity(face_embedding_hash) WHERE face_embedding_hash IS NOT NULL;

-- Full audit trail of every nanny.overall_verification_status change - the "reason" on the nanny
-- row itself only ever holds the current reason; this holds every transition, which is what an
-- incident investigation or a compliance review actually needs. changed_by is nullable because
-- the expiry/re-verification scheduled job changes status with no admin user behind it.
CREATE TABLE nanny_status_history (
    id               BIGSERIAL PRIMARY KEY,
    nanny_id         BIGINT NOT NULL REFERENCES nanny(id) ON DELETE CASCADE,
    previous_status  SMALLINT NOT NULL,
    new_status       SMALLINT NOT NULL,
    reason           VARCHAR(500),
    changed_by       BIGINT REFERENCES users(id),
    changed_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_nanny_status_history_nanny_id ON nanny_status_history(nanny_id, changed_at DESC);
