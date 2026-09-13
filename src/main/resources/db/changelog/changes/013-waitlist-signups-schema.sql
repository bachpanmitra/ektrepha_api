--liquibase formatted sql

--changeset bachpanmitra:13
-- Landing-page notify-me popup: waitlist_signups links a user to the pincode/interest they
-- registered demand for. Also recreates password_reset_tokens, dropped in changeset 4's
-- identity rewrite and never recreated -- the waitlist signup flow needs it to back the
-- "set your password" email link sent to email-based signups.
-- Waitlisted users are identified via the existing user_source = 'WAITLIST' value, so no
-- separate status column is needed here.

CREATE TABLE waitlist_signups (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    pincode    VARCHAR(6) NOT NULL,
    interest   VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_waitlist_signups_pincode ON waitlist_signups(pincode);

CREATE TABLE password_reset_tokens (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token      VARCHAR(255) NOT NULL UNIQUE,
    expires_at TIMESTAMP NOT NULL,
    used       BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_password_reset_tokens_user_id ON password_reset_tokens(user_id);
