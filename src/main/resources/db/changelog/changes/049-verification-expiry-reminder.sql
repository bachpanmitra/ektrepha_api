--liquibase formatted sql

--changeset ektrepha:49
-- 30-days-before-expiry reminder push for a VERIFIED BACKGROUND_CHECK (PCC) (nanny app spec:
-- "remind before a document expires"). Tracks when that reminder was sent so
-- VerificationExpiryAuditJob's reminder sweep never re-sends it on every daily run.
ALTER TABLE nanny_verification ADD COLUMN expiry_reminder_sent_at TIMESTAMPTZ;
