--liquibase formatted sql

--changeset ektrepha:26
-- Allergies is the one care-notes field with a physical-safety consequence on a silent read
-- failure (PRD v2 §16.7 / "PRD API Design Spec" conflict #7) — promoted out of children.meta_data
-- JSONB into a real, typed column instead of staying schemaless with everything else in meta_data.

ALTER TABLE children ADD COLUMN allergies TEXT[] NOT NULL DEFAULT '{}';
