--liquibase formatted sql

--changeset ektrepha:46
-- Elder Care: senior_care's own care-recipient profile. Shaped after children/parent_child
-- (migration 006) rather than owned directly by one parent - a parent_elder join table the same
-- shape as parent_child (relationship, is_primary_contact) so multi-guardian sharing works the
-- same way for an elder as it already does for a child.
--
-- relationship/mobility stay literal VARCHAR with a CHECK constraint (the serviceability schema's
-- convention, migration 008) rather than this codebase's other SMALLINT-coded-enum convention
-- (migration 006) - elders is new enough to not carry that legacy, and VARCHAR keeps the DB value
-- self-describing. relationship has no NOT NULL/DEFAULT (unlike parent_child.relationship) since
-- ElderInput leaves it optional.
--
-- booking.elder_id is the senior_care counterpart of the existing child_id - nullable, and the two
-- are mutually exclusive both at the app layer (HourlyCareServiceImpl#validateCareRecipient) and
-- now via chk_booking_child_xor_elder below.

CREATE TABLE elders (
    id                      BIGSERIAL PRIMARY KEY,
    first_name              VARCHAR(100) NOT NULL,
    last_name               VARCHAR(100),
    age                     INT NOT NULL CHECK (age BETWEEN 40 AND 120),
    gender                  VARCHAR(10),
    mobility                VARCHAR(20) NOT NULL CHECK (mobility IN ('INDEPENDENT', 'NEEDS_SUPPORT', 'WHEELCHAIR', 'BEDRIDDEN')),
    conditions              TEXT[] NOT NULL DEFAULT '{}',
    allergies               TEXT[] NOT NULL DEFAULT '{}',
    medication_notes        VARCHAR(500),
    preferred_language      VARCHAR(30),
    emergency_contact_name  VARCHAR(100),
    emergency_contact_phone VARCHAR(20),
    doctor_name             VARCHAR(100),
    doctor_phone            VARCHAR(20),
    profile_photo_s3_key    VARCHAR(500),
    meta_data               JSONB,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    removed_at              TIMESTAMPTZ
);

CREATE TABLE parent_elder (
    parent_id          BIGINT NOT NULL REFERENCES parent(id) ON DELETE CASCADE,
    elder_id            BIGINT NOT NULL REFERENCES elders(id) ON DELETE CASCADE,
    relationship        VARCHAR(20) CHECK (relationship IN ('FATHER', 'MOTHER', 'GRANDFATHER', 'GRANDMOTHER', 'FATHER_IN_LAW', 'MOTHER_IN_LAW', 'OTHER')),
    is_primary_contact BOOLEAN NOT NULL DEFAULT true,
    PRIMARY KEY (parent_id, elder_id)
);

ALTER TABLE booking ADD COLUMN elder_id BIGINT REFERENCES elders(id);
CREATE INDEX idx_booking_elder_id ON booking (elder_id);
ALTER TABLE booking ADD CONSTRAINT chk_booking_child_xor_elder CHECK (child_id IS NULL OR elder_id IS NULL);
