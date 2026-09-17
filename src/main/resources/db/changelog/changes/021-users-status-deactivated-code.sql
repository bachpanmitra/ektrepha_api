--liquibase formatted sql

--changeset ektrepha:21
-- users.status (added migration 006, soft-delete only: 0=ACTIVE, 1=DELETED) and users.is_active
-- can currently disagree - e.g. is_active=false but status=0 ACTIVE. Add a DEACTIVATED code and
-- migrate any row where the two flags disagree so status becomes the fuller signal. is_active
-- itself is left in place for now (the app still reads it for login gating) and should only be
-- dropped in a later release, once nothing reads it.

ALTER TABLE users DROP CONSTRAINT users_status_check;
UPDATE users SET status = 2 WHERE is_active = false AND status = 0;
ALTER TABLE users ADD CONSTRAINT users_status_check CHECK (status IN (0, 1, 2));

COMMENT ON COLUMN users.status IS '0=ACTIVE, 1=DELETED, 2=DEACTIVATED';
