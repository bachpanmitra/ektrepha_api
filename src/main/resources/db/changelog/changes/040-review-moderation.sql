--liquibase formatted sql

--changeset ektrepha:40
-- Review moderation (product ask: "flag and hide abusive or fake reviews"). A single current-state
-- record on the review row, not a separate history table - a hide is rare and effectively one-way in
-- practice (an admin might restore a wrongly-hidden one, but there's no real "history" to browse
-- beyond who/when/why for the current state, same shape as nanny.status_reason).

ALTER TABLE review ADD COLUMN status VARCHAR(10) NOT NULL DEFAULT 'VISIBLE';
ALTER TABLE review ADD COLUMN moderation_reason VARCHAR(500);
ALTER TABLE review ADD COLUMN moderated_by BIGINT REFERENCES users(id);
ALTER TABLE review ADD COLUMN moderated_at TIMESTAMPTZ;

CREATE INDEX idx_review_status ON review (status);
