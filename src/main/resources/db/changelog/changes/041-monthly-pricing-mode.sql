--liquibase formatted sql

--changeset ektrepha:41
-- Adds MONTHLY as a third pricing mode alongside the existing fixed/range hourly modes - a flat
-- recurring price per area+service (e.g. a full-time childcare retainer), independent of booking
-- hours and of the hourly day-type/demand surge machinery, which has no meaning for a monthly rate.
-- monthly_price follows the same pattern as fix_price/rate_min/rate_max: nullable column, enforced
-- NOT NULL only when pricing_mode = 'monthly' via a CHECK constraint.

ALTER TABLE zone_service_pricing DROP CONSTRAINT zone_service_pricing_pricing_mode_check;
ALTER TABLE zone_service_pricing ADD CONSTRAINT zone_service_pricing_pricing_mode_check
    CHECK (pricing_mode IN ('fixed', 'range', 'monthly'));

ALTER TABLE zone_service_pricing ADD COLUMN monthly_price NUMERIC(10, 2);
ALTER TABLE zone_service_pricing ADD CONSTRAINT chk_zsp_monthly_price
    CHECK (pricing_mode <> 'monthly' OR monthly_price IS NOT NULL);
