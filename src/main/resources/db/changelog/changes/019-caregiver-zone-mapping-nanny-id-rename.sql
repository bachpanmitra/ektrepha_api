--liquibase formatted sql

--changeset ektrepha:19
-- caregiver_zone_mapping.caregiver_id is the only FK to nanny(id) in the schema that isn't named
-- nanny_id (booking, review, nanny_verification, nanny_language, nanny_skill, nanny_service_area
-- all use nanny_id). Renaming the column for consistency; the "caregiver" business vocabulary
-- (CaregiverZoneMapping, CaregiverDemandSignalSource, the caregiverId field on the public pricing
-- API) is intentional multi-vertical terminology and is left as-is - only the DB column name
-- was the actual inconsistency.

ALTER TABLE caregiver_zone_mapping RENAME COLUMN caregiver_id TO nanny_id;
