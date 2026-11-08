--liquibase formatted sql

--changeset ektrepha:52
-- Dedup flag for the same-day ~15-min-before-start push reminder (API Map row 64). The spec's
-- second sweep (an evening-before reminder for tomorrow's bookings) needs its own dedup column on
-- booking instead, since tomorrow's booking_attendance row doesn't exist until the 00:05 daily
-- code-generation job runs - left for a later pass.
ALTER TABLE booking_attendance ADD COLUMN reminder_sent_at TIMESTAMPTZ;
