--liquibase formatted sql

--changeset ektrepha:39
-- Full per-booking activity/audit trail (product ask: admin wants to see exactly when a booking was
-- created, when a caregiver was assigned, when the nanny checked in/out, when payment settled - not
-- just the booking's current-state columns, which can't say *when* something happened). Logged
-- explicitly at each write point (HourlyCareServiceImpl, NannyAttendanceServiceImpl,
-- RazorpayWebhookController) via OrderActivityService rather than reconstructed later or via a DB
-- trigger, so it carries the acting admin's identity (triggers can't see the app's security context).

CREATE TABLE order_activity (
    id BIGSERIAL PRIMARY KEY,
    booking_id BIGINT NOT NULL REFERENCES booking(id),
    event_type VARCHAR(40) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    actor_type VARCHAR(20) NOT NULL,
    actor_id BIGINT,
    actor_name VARCHAR(255),
    metadata JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_order_activity_booking_id ON order_activity (booking_id, occurred_at);
