--liquibase formatted sql

--changeset bachpanmitra:6
-- Nanny marketplace schema: parent/nanny profiles, verification, catalogs
-- (language/skill), service area, children, addresses, bookings, reviews,
-- and tunable ranking weights. References `users` (not `user` — that's
-- the actual table name from changeset 5).
--
-- Adds users.status/deleted_at for soft-delete, since the app logic notes
-- for this schema depend on it but no prior changeset defined it.
--
-- All state/type enums are stored as SMALLINT codes rather than VARCHAR,
-- with the mapping documented via COMMENT ON COLUMN: 2-state fields use
-- 0/1, 3+-state fields use 1..N.

CREATE EXTENSION IF NOT EXISTS cube;
CREATE EXTENSION IF NOT EXISTS earthdistance;
CREATE EXTENSION IF NOT EXISTS btree_gist;

ALTER TABLE users
    ADD COLUMN status SMALLINT NOT NULL DEFAULT 0 CHECK (status IN (0, 1)),
    ADD COLUMN deleted_at TIMESTAMPTZ;
COMMENT ON COLUMN users.status IS '0=ACTIVE, 1=DELETED';

CREATE TABLE parent (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT UNIQUE NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    first_name VARCHAR(100) NOT NULL,
    last_name  VARCHAR(100) NOT NULL,
    meta_data  JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE nanny (
    id                          BIGSERIAL PRIMARY KEY,
    user_id                     BIGINT UNIQUE NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    first_name                  VARCHAR(100) NOT NULL,
    last_name                   VARCHAR(100) NOT NULL,
    bio                         VARCHAR(2000),
    profile_photo_s3_key        VARCHAR(500),
    education_level             VARCHAR(50),
    years_experience            INT,
    hourly_rate                 NUMERIC(10, 2),
    overall_verification_status SMALLINT NOT NULL DEFAULT 1 CHECK (overall_verification_status IN (1, 2, 3, 4)),
    meta_data                   JSONB,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT now()
);
COMMENT ON COLUMN nanny.overall_verification_status IS '1=PENDING, 2=PARTIAL, 3=VERIFIED, 4=REJECTED';

CREATE TABLE nanny_verification (
    id                  BIGSERIAL PRIMARY KEY,
    nanny_id            BIGINT NOT NULL REFERENCES nanny(id) ON DELETE CASCADE,
    type                SMALLINT NOT NULL CHECK (type IN (1, 2, 3, 4, 5)),
    s3_key              VARCHAR(500) NOT NULL,
    status              SMALLINT NOT NULL DEFAULT 1 CHECK (status IN (1, 2, 3)),
    vendor_reference_id VARCHAR(100),
    reviewed_by         BIGINT REFERENCES users(id),
    reviewed_at         TIMESTAMPTZ,
    rejection_reason    VARCHAR(255),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
COMMENT ON COLUMN nanny_verification.type IS '1=ID_PROOF, 2=BACKGROUND_CHECK, 3=EDUCATION, 4=FIRST_AID, 5=REFERENCE';
COMMENT ON COLUMN nanny_verification.status IS '1=PENDING, 2=VERIFIED, 3=REJECTED';

CREATE TABLE language (
    id        BIGSERIAL PRIMARY KEY,
    name      VARCHAR(50) UNIQUE NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT true
);

CREATE TABLE nanny_language (
    nanny_id    BIGINT NOT NULL REFERENCES nanny(id) ON DELETE CASCADE,
    language_id BIGINT NOT NULL REFERENCES language(id),
    proficiency SMALLINT NOT NULL DEFAULT 2 CHECK (proficiency IN (1, 2, 3, 4)),
    PRIMARY KEY (nanny_id, language_id)
);
COMMENT ON COLUMN nanny_language.proficiency IS '1=BASIC, 2=CONVERSATIONAL, 3=FLUENT, 4=NATIVE';

CREATE TABLE skill (
    id   BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) UNIQUE NOT NULL
);

CREATE TABLE nanny_skill (
    nanny_id BIGINT NOT NULL REFERENCES nanny(id) ON DELETE CASCADE,
    skill_id BIGINT NOT NULL REFERENCES skill(id),
    PRIMARY KEY (nanny_id, skill_id)
);

CREATE TABLE nanny_service_area (
    id        BIGSERIAL PRIMARY KEY,
    nanny_id  BIGINT NOT NULL REFERENCES nanny(id) ON DELETE CASCADE,
    lat       DOUBLE PRECISION NOT NULL,
    lng       DOUBLE PRECISION NOT NULL,
    radius_km INT NOT NULL DEFAULT 10
);

CREATE TABLE children (
    id         BIGSERIAL PRIMARY KEY,
    first_name VARCHAR(100) NOT NULL,
    last_name  VARCHAR(100),
    dob        DATE NOT NULL,
    gender     VARCHAR(10),
    meta_data  JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE parent_child (
    parent_id          BIGINT NOT NULL REFERENCES parent(id) ON DELETE CASCADE,
    child_id           BIGINT NOT NULL REFERENCES children(id) ON DELETE CASCADE,
    relationship       SMALLINT NOT NULL DEFAULT 0 CHECK (relationship IN (0, 1)),
    is_primary_contact BOOLEAN NOT NULL DEFAULT true,
    PRIMARY KEY (parent_id, child_id)
);
COMMENT ON COLUMN parent_child.relationship IS '0=PARENT, 1=GUARDIAN';

CREATE TABLE parent_address (
    id            BIGSERIAL PRIMARY KEY,
    parent_id     BIGINT NOT NULL REFERENCES parent(id) ON DELETE CASCADE,
    label         SMALLINT NOT NULL DEFAULT 1 CHECK (label IN (1, 2, 3)),
    address_line1 VARCHAR(255) NOT NULL,
    address_line2 VARCHAR(255),
    landmark      VARCHAR(255),
    access_notes  VARCHAR(255),
    pincode       VARCHAR(6) NOT NULL,
    city          VARCHAR(100) NOT NULL,
    state         VARCHAR(100) NOT NULL,
    country       VARCHAR(100) NOT NULL DEFAULT 'India',
    lat           DOUBLE PRECISION,
    lng           DOUBLE PRECISION,
    is_primary    BOOLEAN NOT NULL DEFAULT true
);
COMMENT ON COLUMN parent_address.label IS '1=HOME, 2=WORK, 3=OTHER';

CREATE TABLE booking (
    id                  BIGSERIAL PRIMARY KEY,
    parent_id           BIGINT NOT NULL REFERENCES parent(id),
    nanny_id            BIGINT NOT NULL REFERENCES nanny(id),
    child_id            BIGINT NOT NULL REFERENCES children(id),
    address_id          BIGINT REFERENCES parent_address(id),
    start_time          TIMESTAMPTZ NOT NULL,
    end_time            TIMESTAMPTZ NOT NULL,
    status              SMALLINT NOT NULL DEFAULT 1 CHECK (status IN (1, 2, 3, 4, 5)),
    cancelled_by        BIGINT REFERENCES users(id),
    cancellation_reason VARCHAR(255),
    total_amount        NUMERIC(10, 2),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT no_overlapping_bookings EXCLUDE USING gist (
        nanny_id WITH =,
        tstzrange(start_time, end_time) WITH &&
    ) WHERE (status IN (1, 2, 3))
);
COMMENT ON COLUMN booking.status IS '1=PENDING, 2=CONFIRMED, 3=IN_PROGRESS, 4=COMPLETED, 5=CANCELLED';

CREATE TABLE review (
    id         BIGSERIAL PRIMARY KEY,
    booking_id BIGINT UNIQUE NOT NULL REFERENCES booking(id),
    parent_id  BIGINT NOT NULL REFERENCES parent(id),
    nanny_id   BIGINT NOT NULL REFERENCES nanny(id),
    rating     SMALLINT NOT NULL CHECK (rating BETWEEN 1 AND 5),
    comment    VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE ranking_config (
    id          BIGSERIAL PRIMARY KEY,
    factor      SMALLINT NOT NULL CHECK (factor IN (1, 2, 3, 4)),
    weight      NUMERIC(4, 3) NOT NULL,
    active_from TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by  BIGINT REFERENCES users(id)
);
COMMENT ON COLUMN ranking_config.factor IS '1=DISTANCE, 2=PRICE, 3=EXPERIENCE, 4=RATING';

CREATE INDEX idx_booking_nanny_time ON booking(nanny_id, start_time);
CREATE INDEX idx_booking_parent_time ON booking(parent_id, start_time);
CREATE INDEX idx_parent_address_geo ON parent_address USING gist (ll_to_earth(lat, lng));
CREATE INDEX idx_nanny_service_area_geo ON nanny_service_area USING gist (ll_to_earth(lat, lng));
CREATE INDEX idx_nanny_verification_nanny ON nanny_verification(nanny_id, type);
CREATE INDEX idx_nanny_language_lookup ON nanny_language(language_id, nanny_id);
CREATE INDEX idx_nanny_skill_lookup ON nanny_skill(skill_id, nanny_id);
CREATE INDEX idx_review_nanny ON review(nanny_id);
CREATE INDEX idx_nanny_verification_status ON nanny(overall_verification_status) WHERE overall_verification_status = 3;
CREATE INDEX idx_ranking_config_active ON ranking_config(factor, active_from DESC);
