--liquibase formatted sql

--changeset ektrepha:51
-- Live "on the way" tracking (nanny app spec: start-trip/arrived/location-ping/ETA, API Map rows
-- 29-31, 51-52, 62). One row per nanny (not per booking) - a nanny only ever has one active trip
-- at a time, and the latest ping always wins regardless of which booking it's attached to.
CREATE TABLE nanny_location (
    nanny_id BIGINT PRIMARY KEY REFERENCES nanny(id),
    booking_id BIGINT NOT NULL REFERENCES booking(id),
    lat DOUBLE PRECISION NOT NULL,
    lng DOUBLE PRECISION NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

-- Append-only event log for a booking's day (trip started, arrived, ...) - BookingEventService is
-- the single shared write path every "on the way" row uses, per the Build Spec's own shared-piece
-- list. type/actor are stored as their enum name (VARCHAR), not a CodedEnum SMALLINT, since this
-- table is expected to grow new event types over time (SOS, incidents, etc. per the Build Order)
-- and a readable value is worth more here than a few bytes.
CREATE TABLE booking_event (
    id BIGSERIAL PRIMARY KEY,
    booking_id BIGINT NOT NULL REFERENCES booking(id),
    event_date DATE NOT NULL,
    type VARCHAR(30) NOT NULL,
    lat DOUBLE PRECISION,
    lng DOUBLE PRECISION,
    actor VARCHAR(20) NOT NULL,
    meta JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_booking_event_booking_date_type ON booking_event(booking_id, event_date, type);
