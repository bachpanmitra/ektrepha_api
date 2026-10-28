--liquibase formatted sql

--changeset ektrepha:49
-- Ops manager teams: one ops manager (an ADMIN user) looks after many helpers. Until now every
-- ADMIN saw every nanny with no notion of "my team"; manager_user_id is that link. Nullable - a
-- helper not yet given a manager still shows up in the all-nannies admin screens as before.
ALTER TABLE nanny ADD COLUMN manager_user_id BIGINT REFERENCES users(id);
CREATE INDEX idx_nanny_manager_user_id ON nanny (manager_user_id);

-- What kind of helper this is. The nanny table holds every in-home caregiver, not just nannies -
-- elder carers and nurses included - so ops can filter a team by role. Literal VARCHAR + CHECK,
-- the convention elders.mobility (migration 046) uses.
ALTER TABLE nanny ADD COLUMN caregiver_role VARCHAR(20) NOT NULL DEFAULT 'NANNY'
    CHECK (caregiver_role IN ('NANNY', 'ELDER_CARER', 'NURSE'));
