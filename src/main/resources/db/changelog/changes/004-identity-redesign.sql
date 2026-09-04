--liquibase formatted sql

--changeset bachpanmitra:4
-- Full identity-model rewrite: multi-channel auth (Google / Phone / Email),
-- UUID primary keys, OTP-based verification and password reset. Replaces
-- the single-channel email+password model from changeset 3 entirely —
-- no production data exists on the old shape yet.

CREATE EXTENSION IF NOT EXISTS pgcrypto;

DROP TABLE IF EXISTS password_reset_tokens;
DROP TABLE IF EXISTS refresh_tokens;
DROP TABLE IF EXISTS users;

CREATE TABLE users (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
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
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id        UUID REFERENCES users(id) ON DELETE CASCADE,
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
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token      VARCHAR(500) NOT NULL UNIQUE,
    expires_at TIMESTAMP NOT NULL,
    revoked    BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens(user_id);
