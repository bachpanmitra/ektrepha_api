--liquibase formatted sql

--changeset ektrepha:42
-- Dedicated, typed cancellation audit trail - who cancelled a booking, why (a reason_code ops can
-- report on, plus the free-text reason), and what status it was in beforehand. booking.status/
-- cancelled_by/cancellation_reason only ever hold the *current* cancellation (and get overwritten
-- if that ever changes), and order_activity's BOOKING_CANCELLED row stores its reason inside a
-- generic jsonb metadata blob rather than a queryable column - this table exists so "how many
-- bookings were cancelled for X reason last month" is a plain GROUP BY, not a JSON scan. Logged
-- from every cancellation path: parent self-serve cancel, the admin override-cancel, and the
-- stale-booking auto-cancel sweep.

CREATE TABLE booking_cancellation_log (
    id BIGSERIAL PRIMARY KEY,
    booking_id BIGINT NOT NULL REFERENCES booking(id),
    cancelled_by_user_id BIGINT REFERENCES users(id),
    actor_type VARCHAR(20) NOT NULL,
    reason_code VARCHAR(40) NOT NULL,
    reason VARCHAR(500),
    previous_status SMALLINT NOT NULL,
    cancelled_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_booking_cancellation_log_booking_id ON booking_cancellation_log (booking_id);
