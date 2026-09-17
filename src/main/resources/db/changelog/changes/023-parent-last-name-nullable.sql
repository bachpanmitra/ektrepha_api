--liquibase formatted sql

--changeset ektrepha:23
-- Single-name users are common in India (PRD v2 "Parent-Side Profile & Booking Surfaces" §6,
-- conflict #2) — parent.last_name must not force a value that doesn't exist.

ALTER TABLE parent ALTER COLUMN last_name DROP NOT NULL;
