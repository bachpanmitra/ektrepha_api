--liquibase formatted sql

--changeset ektrepha:24
-- P1 (Parent Profile) shows an avatar. parent had no photo column — nanny already has one.

ALTER TABLE parent ADD COLUMN profile_photo_s3_key VARCHAR(500);
