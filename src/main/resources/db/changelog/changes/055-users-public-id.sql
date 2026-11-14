--liquibase formatted sql

--changeset ektrepha:55
-- Care Manager admin-API responses (Assignment.assigned_by, AccessLogEntry.actor) reference the
-- acting Admin user by a public id, same pattern as migration 053's parent/nanny/booking.public_id
-- — only ever surfaced for ADMIN-role users by this feature (adm_ prefix), never for PARENT/NANNY/
-- CARE_MANAGER, so one shared column on users is simpler than per-role columns.
ALTER TABLE users ADD COLUMN public_id VARCHAR(20);
UPDATE users SET public_id = 'adm_' || to_hex(id) WHERE public_id IS NULL;
ALTER TABLE users ALTER COLUMN public_id SET NOT NULL;
ALTER TABLE users ADD CONSTRAINT uq_users_public_id UNIQUE (public_id);
