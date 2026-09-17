--liquibase formatted sql

--changeset ektrepha:27
-- A5 (Notification Settings) and any push-driven flow (B3 live care, review prompts) need
-- somewhere to store per-category channel opt-outs and device push tokens. Schema only in this
-- pass — no entity/repository/service/controller wired up yet; A5 itself is out of scope until
-- the delivery pipeline (push provider, SMS/email triggers) is built.

CREATE TABLE user_notification_preference (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    category VARCHAR(30) NOT NULL,
    push_enabled BOOLEAN NOT NULL DEFAULT true,
    sms_enabled BOOLEAN NOT NULL DEFAULT true,
    email_enabled BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, category)
);

COMMENT ON TABLE user_notification_preference IS 'Per-category channel opt-outs (A5). Transactional-safety categories (booking cancelled, care started) are enforced non-opt-outable at the app layer, not here.';

CREATE TABLE user_device (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    push_token VARCHAR(500) NOT NULL,
    platform VARCHAR(10) NOT NULL CHECK (platform IN ('ios', 'android')),
    last_active_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (push_token)
);

CREATE INDEX idx_user_device_user_id ON user_device(user_id);
