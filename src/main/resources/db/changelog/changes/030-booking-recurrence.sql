--liquibase formatted sql

--changeset ektrepha:30
-- Recurring bookings: "book every day/week" and month-base (monthly) types, generated as sibling
-- rows in `booking` at creation time (BookingWriteServiceImpl), not expanded lazily. frequency
-- reuses the BookingFrequency enum already used by booking_requests (migration 014).
-- recurrence_group_id points at the series' anchor (first) booking; NULL for ONE_TIME bookings,
-- and set to its own id on the anchor row so "all bookings in a series" is one equality filter.

ALTER TABLE booking ADD COLUMN frequency SMALLINT NOT NULL DEFAULT 1;
ALTER TABLE booking ADD COLUMN recurrence_group_id BIGINT REFERENCES booking(id);

COMMENT ON COLUMN booking.frequency IS '1=ONE_TIME, 2=REPEAT_WEEKLY, 3=REPEAT_DAILY, 4=REPEAT_MONTHLY';

CREATE INDEX idx_booking_recurrence_group_id ON booking(recurrence_group_id);
