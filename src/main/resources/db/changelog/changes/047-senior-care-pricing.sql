--liquibase formatted sql

--changeset ektrepha:47
-- Elder Care launches priced identically to childcare in every zone that already serves it,
-- rather than requiring ops to configure a second full set of zone_service_pricing rows by hand.
-- A straight row-for-row copy (not a PricingServiceImpl-level fallback) so senior_care has real,
-- independently-editable pricing/serviceability data from day one - ops can later diverge a given
-- zone's senior_care price without touching this migration's output.

-- One zone_service_pricing row per zone that has an active childcare row, copying every pricing
-- column across. ON CONFLICT DO NOTHING - safe to re-run, and never overwrites a zone that was
-- already given its own senior_care row some other way.
INSERT INTO zone_service_pricing (
    zone_area_id, service_type_id, pricing_mode, fix_price, rate_min, rate_max, unit_price,
    monthly_price, currency, min_booking_hours, platform_fee_pct, is_active, created_at, updated_at)
SELECT
    zsp.zone_area_id, st_senior.id, zsp.pricing_mode, zsp.fix_price, zsp.rate_min, zsp.rate_max, zsp.unit_price,
    zsp.monthly_price, zsp.currency, zsp.min_booking_hours, zsp.platform_fee_pct, zsp.is_active, now(), now()
FROM zone_service_pricing zsp
JOIN service_types st_child ON st_child.id = zsp.service_type_id AND st_child.code = 'childcare'
JOIN service_types st_senior ON st_senior.code = 'senior_care'
ON CONFLICT (zone_area_id, service_type_id) DO NOTHING;

-- Carry over each copied row's dynamic_pricing_config too, so senior_care surge pricing behaves
-- exactly like childcare's in the same zone rather than silently defaulting to disabled.
INSERT INTO dynamic_pricing_config (
    zone_service_pricing_id, is_enabled, demand_threshold_low, demand_threshold_high,
    min_multiplier, max_multiplier, recompute_interval_mins, created_at, updated_at)
SELECT
    zsp_senior.id, dpc.is_enabled, dpc.demand_threshold_low, dpc.demand_threshold_high,
    dpc.min_multiplier, dpc.max_multiplier, dpc.recompute_interval_mins, now(), now()
FROM dynamic_pricing_config dpc
JOIN zone_service_pricing zsp_child ON zsp_child.id = dpc.zone_service_pricing_id
JOIN service_types st_child ON st_child.id = zsp_child.service_type_id AND st_child.code = 'childcare'
JOIN zone_service_pricing zsp_senior ON zsp_senior.zone_area_id = zsp_child.zone_area_id
JOIN service_types st_senior ON st_senior.id = zsp_senior.service_type_id AND st_senior.code = 'senior_care'
ON CONFLICT (zone_service_pricing_id) DO NOTHING;

-- Serviceability follows the same zones/status as childcare (LIVE where childcare is LIVE,
-- coming_soon/not_planned likewise) - Elder Care launches everywhere childcare already does.
INSERT INTO serviceability_service_type (zone_area_id, service_type_id, status, launched_at, created_at, updated_at)
SELECT sst.zone_area_id, st_senior.id, sst.status, sst.launched_at, now(), now()
FROM serviceability_service_type sst
JOIN service_types st_child ON st_child.id = sst.service_type_id AND st_child.code = 'childcare'
JOIN service_types st_senior ON st_senior.code = 'senior_care'
ON CONFLICT (zone_area_id, service_type_id) DO NOTHING;

INSERT INTO skill (name) VALUES
    ('Elder care'),
    ('Dementia care'),
    ('Mobility assistance'),
    ('Medication management'),
    ('Post-hospital care')
ON CONFLICT (name) DO NOTHING;
