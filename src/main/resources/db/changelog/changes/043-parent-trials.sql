--liquibase formatted sql

--changeset ektrepha:43
-- FIRST5_FEE_WAIVER: new parents pay zero amount at all (subtotal + platform fee + GST-on-fee) on
-- hourly-care bookings whose checkout completes within a configurable window (default 120h) of
-- their first OTP verification. free_hours_used is a cumulative usage counter, not a per-booking
-- limit — every booking made while ACTIVE is fully free regardless of its own length; the trial
-- simply ends once a confirmed booking's hours push the running total to/past app.trial.free-hours-cap.
-- One trial per person ever.
--
-- id is a plain UUID (generated in application code via UUID.randomUUID(), no DB default) rather
-- than following this project's usual BIGSERIAL convention - migration 022 dropped pgcrypto as
-- unused and there's no other UUID column anywhere in this schema to lean on gen_random_uuid()
-- for, so generating it in Java avoids a new, untested extension dependency.
--
-- address_hash isn't in the original spec's column list but is required by its own abuse-check
-- rule ("or the care address is already used on another trial") - there's nowhere else to persist
-- that signal. The three *_sent_at timestamps aren't in the spec either; they exist purely so the
-- 5-minute notification/expiry sweep (TrialNotificationJob/TrialExpiryJob) never double-sends a
-- push or the ENDED email across repeated runs.
CREATE TABLE parent_trials (
    id UUID PRIMARY KEY,
    parent_id BIGINT NOT NULL UNIQUE REFERENCES parent(id) ON DELETE CASCADE,
    promo_code VARCHAR(40) NOT NULL DEFAULT 'FIRST5_FEE_WAIVER',
    status SMALLINT NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ NOT NULL,
    ended_reason SMALLINT,
    free_hours_used NUMERIC(6,2) NOT NULL DEFAULT 0,
    device_id_hash VARCHAR(64),
    phone_hash VARCHAR(64) NOT NULL,
    payment_fingerprint VARCHAR(64),
    address_hash VARCHAR(64),
    day2_push_sent_at TIMESTAMPTZ,
    expiry_reminder_push_sent_at TIMESTAMPTZ,
    ended_email_sent_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_parent_trials_phone_hash ON parent_trials (phone_hash);
CREATE INDEX idx_parent_trials_device_id_hash ON parent_trials (device_id_hash);
CREATE INDEX idx_parent_trials_payment_fingerprint ON parent_trials (payment_fingerprint);
CREATE INDEX idx_parent_trials_address_hash ON parent_trials (address_hash);
CREATE INDEX idx_parent_trials_status_ends_at ON parent_trials (status, ends_at);

-- Ties a booking back to the trial that discounted it, and by how much, so cancelling/reading a
-- booking never has to re-derive the discount. trial_id is nullable - most bookings never touch
-- a trial at all.
ALTER TABLE booking ADD COLUMN trial_id UUID REFERENCES parent_trials(id);
ALTER TABLE booking ADD COLUMN trial_discount_paise BIGINT NOT NULL DEFAULT 0;
CREATE INDEX idx_booking_trial_id ON booking (trial_id);

-- Locked, short-lived (default 15 min) price quote for the hourly-care "pay first" flow
-- (com.ektrepha.hourlycare) - the only place a trial discount can apply. Persisted (not
-- recomputed) specifically so a trial discount agreed at quote time survives unchanged through to
-- payment, even if the trial's own counters/caps move in between.
CREATE TABLE hourly_care_quote (
    id UUID PRIMARY KEY,
    parent_id BIGINT NOT NULL REFERENCES parent(id),
    zone_area_id BIGINT NOT NULL REFERENCES zone_areas(id),
    address_id BIGINT NOT NULL REFERENCES parent_address(id),
    child_id BIGINT REFERENCES children(id),
    pricing_mode VARCHAR(10) NOT NULL,
    start_time TIMESTAMPTZ NOT NULL,
    end_time TIMESTAMPTZ NOT NULL,
    subtotal_paise BIGINT NOT NULL,
    platform_fee_paise BIGINT NOT NULL,
    platform_fee_gst_paise BIGINT NOT NULL DEFAULT 0,
    trial_id UUID REFERENCES parent_trials(id),
    trial_discount_paise BIGINT NOT NULL DEFAULT 0,
    payable_paise BIGINT NOT NULL,
    full_payable_paise BIGINT NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'INR',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ
);

CREATE INDEX idx_hourly_care_quote_parent_id ON hourly_care_quote (parent_id);
CREATE INDEX idx_hourly_care_quote_expires_at ON hourly_care_quote (expires_at);
