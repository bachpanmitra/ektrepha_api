--liquibase formatted sql

--changeset ektrepha:31
-- Hourly-care "pay first, we assign later" flow (see com.ektrepha.hourlycare): the parent picks a
-- slot without choosing a nanny, pays up front, and Ektrepha ops assigns an in-house caregiver
-- afterwards. booking.nanny_id must become nullable to hold a booking in that pre-assignment
-- window; the no_overlapping_bookings EXCLUDE constraint (migration 006) already no-ops for a NULL
-- nanny_id (Postgres never treats NULL = NULL as a match), so real capacity checks for these rows
-- happen at the application layer (zone/service capacity in HourlyCareServiceImpl), not via that
-- constraint. It re-engages normally once ops assigns a real nanny_id and flips status to CONFIRMED.

ALTER TABLE booking ALTER COLUMN nanny_id DROP NOT NULL;
ALTER TABLE booking ADD COLUMN care_notes VARCHAR(500);

ALTER TABLE booking DROP CONSTRAINT booking_status_check;
ALTER TABLE booking ADD CONSTRAINT booking_status_check CHECK (status IN (1, 2, 3, 4, 5, 6, 7));
COMMENT ON COLUMN booking.status IS '1=PENDING, 2=CONFIRMED, 3=IN_PROGRESS, 4=COMPLETED, 5=CANCELLED, 6=AWAITING_PAYMENT, 7=ASSIGNING_CAREGIVER';

-- No payment gateway is integrated yet (model only) - this table tracks the parent-declared
-- method and a confirm/fail call standing in for a real gateway webhook. provider_reference stays
-- unused until a real gateway is wired up.
CREATE TABLE payment_transaction (
    id                  BIGSERIAL PRIMARY KEY,
    booking_id          BIGINT NOT NULL REFERENCES booking(id),
    amount              NUMERIC(10, 2) NOT NULL,
    method              SMALLINT NOT NULL,
    status              SMALLINT NOT NULL,
    provider_reference  VARCHAR(100),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
COMMENT ON COLUMN payment_transaction.method IS '1=UPI, 2=CARD';
COMMENT ON COLUMN payment_transaction.status IS '1=INITIATED, 2=SUCCESS, 3=FAILED';

CREATE INDEX idx_payment_transaction_booking_id ON payment_transaction(booking_id);
