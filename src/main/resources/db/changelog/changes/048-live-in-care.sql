--liquibase formatted sql

--changeset ektrepha:48
-- Live-in care: an in-house helper who lives in the family's home to look after a child
-- (childcare) or an elder (senior_care), booked in whole months. Not a new service type - the
-- care recipient, zone serviceability and caregiver zone mapping are exactly the vertical's
-- existing ones; only the price and the arrangement differ.
--
-- Pricing: live_in_monthly_price is a fourth independently-optional product on the zone+service
-- row, alongside fix_price/rate_min+rate_max/monthly_price (see migration 041's same pattern).
-- Deliberately separate from monthly_price - a visiting monthly retainer and a live-in helper are
-- priced very differently, and a zone may offer one without the other.

ALTER TABLE zone_service_pricing ADD COLUMN live_in_monthly_price NUMERIC(10, 2);

ALTER TABLE zone_service_pricing DROP CONSTRAINT zone_service_pricing_pricing_mode_check;
ALTER TABLE zone_service_pricing ADD CONSTRAINT zone_service_pricing_pricing_mode_check
    CHECK (pricing_mode IN ('fixed', 'range', 'monthly', 'live_in'));
ALTER TABLE zone_service_pricing ADD CONSTRAINT chk_zsp_live_in_price
    CHECK (pricing_mode <> 'live_in' OR live_in_monthly_price IS NOT NULL);

-- Bookings: a live-in stay is stored the same way a monthly-care series is (migration 030) - one
-- booking row per on-duty calendar day (00:00-24:00 IST), tied together by recurrence_group_id,
-- with frequency = 5 (LIVE_IN). The weekly off day simply has no row. Back-to-back day rows are
-- adjacent, not overlapping, under tstzrange's default '[)' bounds, so the
-- no_overlapping_bookings EXCLUDE constraint (migration 006) blocks the helper for the whole stay
-- without tripping over itself.
COMMENT ON COLUMN booking.frequency IS '1=ONE_TIME, 2=REPEAT_WEEKLY, 3=REPEAT_DAILY, 4=REPEAT_MONTHLY, 5=LIVE_IN';

-- What the family has agreed to provide and expect - one row per live-in stay, keyed by the
-- series' anchor booking (the id the parent pays against and ops assigns against). Shown to the
-- helper in the nanny app before they move in.
CREATE TABLE live_in_arrangement (
    id                BIGSERIAL PRIMARY KEY,
    anchor_booking_id BIGINT NOT NULL UNIQUE REFERENCES booking(id) ON DELETE CASCADE,
    start_date        DATE NOT NULL,
    end_date          DATE NOT NULL,
    months            INT NOT NULL CHECK (months BETWEEN 1 AND 3),
    -- NULL = no fixed weekly off (e.g. the family arranges cover themselves).
    weekly_off_day    VARCHAR(10) CHECK (weekly_off_day IN ('MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY')),
    -- The helper's working hours within each on-duty day; outside them the helper is off duty but still at home.
    duty_start_time   TIME NOT NULL,
    duty_end_time     TIME NOT NULL,
    accommodation     VARCHAR(20) NOT NULL CHECK (accommodation IN ('PRIVATE_ROOM', 'SHARED_ROOM')),
    meals_provided    BOOLEAN NOT NULL DEFAULT true,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_live_in_dates CHECK (end_date >= start_date),
    CONSTRAINT chk_live_in_duty_hours CHECK (duty_end_time > duty_start_time)
);
