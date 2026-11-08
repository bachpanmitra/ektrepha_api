--liquibase formatted sql

--changeset ektrepha:47
-- Per-day OTP-gated attendance, replacing migration 037's plain booking.checked_in_at/checked_out_at
-- (nanny app spec: Ektrepha_Nanny_API_Map.xlsx, "DB Changes" + "OTP Rules" sheets). A live-in/monthly
-- booking is already one `booking` row per working day (HourlyCareServiceImpl#createMonthlyBooking),
-- so this is 1:1 with a booking row in practice, but kept as its own table rather than more columns
-- on booking: it carries OTP-specific state (hashes, attempt/lock, method, manual-override audit)
-- that has nothing to do with the booking itself, and a unique(booking_id, attendance_date)
-- defensively matches the spec's "one row per booking per day" even though a booking row today
-- never spans more than one day. booking.checked_in_at/checked_out_at are left as-is for old data;
-- nothing in the new flow writes to them.
CREATE TABLE booking_attendance (
    id BIGSERIAL PRIMARY KEY,
    booking_id BIGINT NOT NULL REFERENCES booking(id),
    attendance_date DATE NOT NULL,
    check_in_otp_hash VARCHAR(100),
    check_out_otp_hash VARCHAR(100),
    check_in_attempts SMALLINT NOT NULL DEFAULT 0,
    check_out_attempts SMALLINT NOT NULL DEFAULT 0,
    locked_until TIMESTAMPTZ,
    checked_in_at TIMESTAMPTZ,
    checked_out_at TIMESTAMPTZ,
    check_in_lat DOUBLE PRECISION,
    check_in_lng DOUBLE PRECISION,
    check_out_lat DOUBLE PRECISION,
    check_out_lng DOUBLE PRECISION,
    check_in_method VARCHAR(10),
    check_out_method VARCHAR(10),
    manual_by_user_id BIGINT REFERENCES users(id),
    manual_reason TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_booking_attendance_booking_date UNIQUE (booking_id, attendance_date)
);

CREATE INDEX idx_booking_attendance_booking_id ON booking_attendance(booking_id);
