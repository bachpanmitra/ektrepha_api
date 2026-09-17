--liquibase formatted sql

--changeset ektrepha:18
-- booking (migration 006) was built childcare-only: child_id is required and there's no
-- service_type_id, even though service_types (migration 008) already lists senior_care,
-- pet_care, tutoring, housekeeping, adult_care. Backfill existing rows to 'childcare' (the
-- only vertical that has ever written to this table) and relax child_id since non-childcare
-- bookings have no care-recipient to attach. Enforcing "child_id required when childcare" is
-- left to the app layer for now; a DB trigger can be added later if bad rows show up in practice.

ALTER TABLE booking ADD COLUMN service_type_id BIGINT REFERENCES service_types(id);

UPDATE booking
SET service_type_id = (SELECT id FROM service_types WHERE code = 'childcare');

ALTER TABLE booking ALTER COLUMN service_type_id SET NOT NULL;
ALTER TABLE booking ALTER COLUMN child_id DROP NOT NULL;

CREATE INDEX idx_booking_service_type ON booking(service_type_id);
