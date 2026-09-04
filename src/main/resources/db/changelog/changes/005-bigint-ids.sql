--liquibase formatted sql

--changeset bachpanmitra:5
-- Revert id columns from UUID back to BIGSERIAL/BIGINT across all three
-- identity tables. No production data on the UUID shape yet, so drop and
-- recreate rather than attempt an in-place UUID->bigint conversion.

DROP TABLE IF EXISTS otps;
DROP TABLE IF EXISTS refresh_tokens;
DROP TABLE IF EXISTS users;

CREATE TABLE users (
    id             BIGSERIAL PRIMARY KEY,
    name           VARCHAR(255),
    email          VARCHAR(255) UNIQUE,
    phone          VARCHAR(20) UNIQUE,
    password       VARCHAR(255),
    google_id      VARCHAR(255) UNIQUE,
    is_active      BOOLEAN NOT NULL DEFAULT TRUE,
    email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    phone_verified BOOLEAN NOT NULL DEFAULT FALSE,
    user_source    VARCHAR(20) NOT NULL,
    user_type      VARCHAR(20) NOT NULL,
    created_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE otps (
    id             BIGSERIAL PRIMARY KEY,
    user_id        BIGINT REFERENCES users(id) ON DELETE CASCADE,
    phone_or_email VARCHAR(255) NOT NULL,
    otp            VARCHAR(255) NOT NULL,
    purpose        VARCHAR(20) NOT NULL,
    attempt_count  INT NOT NULL DEFAULT 0,
    expires_at     TIMESTAMP NOT NULL,
    is_used        BOOLEAN NOT NULL DEFAULT FALSE,
    created_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_otps_phone_or_email_purpose ON otps(phone_or_email, purpose);
CREATE INDEX idx_otps_user_id ON otps(user_id);

CREATE TABLE refresh_tokens (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token      VARCHAR(500) NOT NULL UNIQUE,
    expires_at TIMESTAMP NOT NULL,
    revoked    BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens(user_id);
