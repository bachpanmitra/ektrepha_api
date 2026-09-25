--liquibase formatted sql

--changeset ektrepha:33
-- Mobile-number OTP login/auto-signup. challenge_id lets the API hand the client an opaque id
-- instead of the raw phone number; multiple NULLs are allowed under a UNIQUE constraint in
-- Postgres, so this is safe alongside existing (non-challenge) email OTP rows. session_expires_at
-- enforces a hard session ceiling that is copied forward unchanged on every refresh-token
-- rotation (see AuthServiceImpl#issueTokens), so refreshing never extends the original session.

ALTER TABLE otps ADD COLUMN challenge_id VARCHAR(64) UNIQUE;

ALTER TABLE refresh_tokens ADD COLUMN session_expires_at TIMESTAMP;
