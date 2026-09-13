--liquibase formatted sql

--changeset ektrepha:14
-- Lightweight booking-interest capture from the price-quote flow. No nanny/child assignment yet
-- (that selection doesn't exist in the quote flow) -- just what the user asked for and the price
-- they were quoted, tied to their identified account (see UserController#identify).

CREATE TABLE booking_requests (
    id               BIGSERIAL PRIMARY KEY,
    user_id          BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    zone_area_id     BIGINT NOT NULL REFERENCES zone_areas(id),
    service_type_id  BIGINT NOT NULL REFERENCES service_types(id),
    booking_date     DATE NOT NULL,
    start_time       TIME NOT NULL,
    end_time         TIME NOT NULL,
    quoted_total     NUMERIC(10,2),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_booking_requests_user_id ON booking_requests(user_id);
