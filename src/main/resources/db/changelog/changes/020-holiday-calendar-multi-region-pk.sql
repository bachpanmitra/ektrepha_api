--liquibase formatted sql

--changeset ektrepha:20
-- holiday_calendar's PK was holiday_date alone, which can't hold two different regional holidays
-- landing on the same date. The platform now spans Bangalore (Karnataka), Gurgaon (Haryana),
-- Noida (UP), and Patna (Bihar), so this collides. Switch to a surrogate id, backfill existing
-- (currently all-national) rows to region='ALL', and enforce uniqueness on (holiday_date, region)
-- instead. day_type resolution must now look up WHERE holiday_date = ? AND region IN (?, 'ALL')
-- so a state-specific holiday and a national one on the same date both resolve correctly.

ALTER TABLE holiday_calendar ADD COLUMN id BIGSERIAL;
ALTER TABLE holiday_calendar ALTER COLUMN region SET DEFAULT 'ALL';
UPDATE holiday_calendar SET region = 'ALL' WHERE region IS NULL;
ALTER TABLE holiday_calendar ALTER COLUMN region SET NOT NULL;

ALTER TABLE holiday_calendar DROP CONSTRAINT holiday_calendar_pkey;
ALTER TABLE holiday_calendar ADD PRIMARY KEY (id);
ALTER TABLE holiday_calendar ADD CONSTRAINT uq_holiday_date_region UNIQUE (holiday_date, region);
