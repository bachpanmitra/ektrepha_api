--liquibase formatted sql

--changeset ektrepha:29
-- A parent row can now be auto-vivified (stub, no name yet) the first time a user creates a child
-- or address before ever completing P1/P2 (progressive profile completion, PRD v2 §16.3) — the API
-- upserts the name in later via PUT /api/v1/parents/me, same as migration 023 did for last_name.

ALTER TABLE parent ALTER COLUMN first_name DROP NOT NULL;
