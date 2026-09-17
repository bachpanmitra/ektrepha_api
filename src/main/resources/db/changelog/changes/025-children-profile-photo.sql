--liquibase formatted sql

--changeset ektrepha:25
-- C1/C2/C3 show a child avatar.

ALTER TABLE children ADD COLUMN profile_photo_s3_key VARCHAR(500);
