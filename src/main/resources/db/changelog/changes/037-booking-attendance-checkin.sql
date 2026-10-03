--liquibase formatted sql

--changeset ektrepha:37
-- Nanny-driven attendance: check-in/check-out timestamps on the booking itself, independent of
-- `status` (the start/complete-care lifecycle owned by NannyBookingServiceImpl). Kept as two plain
-- nullable columns rather than a separate table — it's strictly 1:1 with a booking, and this is
-- what finally backs the admin dashboard's long-hardcoded lateOrNoCheckIn KPI and the Attendance tab.

ALTER TABLE booking ADD COLUMN checked_in_at TIMESTAMPTZ;
ALTER TABLE booking ADD COLUMN checked_out_at TIMESTAMPTZ;
