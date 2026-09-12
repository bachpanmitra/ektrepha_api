--liquibase formatted sql

--changeset bachpanmitra:10
-- Reference starting pricing for childcare across the 4 Bangalore zones seeded in changeset 9 -
-- real market-rate numbers, not placeholders, so a fresh deployment has sensible defaults instead
-- of empty pricing (which would make every /pricing/calculate call 404 as "not serviceable"
-- until an admin manually configures every zone). Ops can retune any of this via the
-- /api/v1/admin/pricing endpoints at any time - this is a starting point, not a fixed policy.
--
-- Rate range (INR 180-320/hour) is grounded in real 2026 Bangalore market data: ERI
-- SalaryExpert/erieri.com puts the average Bangalore nanny rate at ~INR 237/hour (range
-- INR 375,252-574,234/year) and the average babysitter rate at ~INR 133-142/hour; Care.com's
-- 2026 Cost of Care Report separately puts its (US) posted after-school-sitter rate at
-- USD 21.87/hour, i.e. the same "babysitter vs. nanny" rate spread this range is built to cover.
-- Platform fee (10%) is a reasonable, commonly-used marketplace commission starting point, not
-- from a specific source - tune via admin API once real unit economics are known.
--
-- Marks childcare LIVE in all 4 zones to match having real pricing configured for it; every other
-- service type stays at its migration-008 default (NOT_PLANNED) until deliberately rolled out.
--
-- Both inserts are ON CONFLICT DO NOTHING against each table's existing (zone_area_id,
-- service_type_id) unique constraint - if an admin already configured childcare for one of these
-- zones before this migration ran, that manual configuration wins, not this reference default.

INSERT INTO serviceability_service_type (zone_area_id, service_type_id, status, launched_at)
SELECT za.id, st.id, 'live', now()
FROM zone_areas za
CROSS JOIN service_types st
WHERE za.name IN ('Bangalore East', 'Bangalore GPO', 'Bangalore South', 'Bangalore West')
  AND st.code = 'childcare'
ON CONFLICT (zone_area_id, service_type_id) DO NOTHING;

INSERT INTO zone_service_pricing (zone_area_id, service_type_id, pricing_mode, rate_min, rate_max, currency, min_booking_hours, platform_fee_pct)
SELECT za.id, st.id, 'range', 180.00, 320.00, 'INR', 2, 10.00
FROM zone_areas za
CROSS JOIN service_types st
WHERE za.name IN ('Bangalore East', 'Bangalore GPO', 'Bangalore South', 'Bangalore West')
  AND st.code = 'childcare'
ON CONFLICT (zone_area_id, service_type_id) DO NOTHING;
