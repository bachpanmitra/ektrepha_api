--liquibase formatted sql

--changeset ektrepha:17
-- The price-quote form captures frequency, children count/age, and care notes, but the original
-- booking_requests table (migration 014) had nowhere to put them. Coded as SMALLINT following
-- this codebase's booking-domain convention (migration 006 / BookingStatus) rather than the
-- VARCHAR+CHECK convention used in the serviceability/pricing domain (migration 008).

ALTER TABLE booking_requests
    ADD COLUMN frequency       SMALLINT NOT NULL DEFAULT 1 CHECK (frequency IN (1, 2)),
    ADD COLUMN children_count  SMALLINT NOT NULL DEFAULT 1 CHECK (children_count > 0),
    ADD COLUMN child_age_years SMALLINT CHECK (child_age_years IS NULL OR child_age_years >= 0),
    ADD COLUMN care_notes      VARCHAR(500);

COMMENT ON COLUMN booking_requests.frequency IS '1=ONE_TIME, 2=REPEAT_WEEKLY';
