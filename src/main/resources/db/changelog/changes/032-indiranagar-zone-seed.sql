--liquibase formatted sql

--changeset bachpanmitra:32
-- Changesets 11/12 both assume an "Indiranagar" zone covering pincode 560038 already exists,
-- seeded manually in production before changeset 9 ran - so changeset 11 deliberately deletes the
-- 560038 row it inherited from the old "Bangalore East" division (along with the rest of that
-- coarse zone's data) without recreating it, and changeset 12 skips it for the same reason. That
-- assumption doesn't hold on a fresh database (dev/CI/any new environment migrated from scratch):
-- there, 560038 ends up with no zone, no serviceability row, and no childcare pricing at all,
-- making every booking/pricing call against it 404 as "not serviceable".
--
-- This backfills exactly that gap, mirroring changeset 10's reference pricing for the other
-- original zones (same rate range/platform fee, childcare marked live). Every insert is ON
-- CONFLICT DO NOTHING against the same unique constraints changesets 9/10 rely on, so this is a
-- no-op wherever the real manually-seeded Indiranagar zone already exists (prod) - it only fills
-- the gap where it doesn't.

INSERT INTO zone_areas (name, city, state, centroid_lat, centroid_lng) VALUES
('Indiranagar', 'Bangalore', 'Karnataka', 12.9784, 77.6408)
ON CONFLICT (name) DO NOTHING;

INSERT INTO serviceability_pincode (pincode, zone_area_id, is_serviceable, status)
SELECT '560038', id, true, 'live' FROM zone_areas WHERE name = 'Indiranagar'
ON CONFLICT (pincode) DO NOTHING;

INSERT INTO serviceability_service_type (zone_area_id, service_type_id, status, launched_at)
SELECT za.id, st.id, 'live', now()
FROM zone_areas za
CROSS JOIN service_types st
WHERE za.name = 'Indiranagar' AND st.code = 'childcare'
ON CONFLICT (zone_area_id, service_type_id) DO NOTHING;

INSERT INTO zone_service_pricing (zone_area_id, service_type_id, pricing_mode, rate_min, rate_max, currency, min_booking_hours, platform_fee_pct)
SELECT za.id, st.id, 'range', 180.00, 320.00, 'INR', 2, 10.00
FROM zone_areas za
CROSS JOIN service_types st
WHERE za.name = 'Indiranagar' AND st.code = 'childcare'
ON CONFLICT (zone_area_id, service_type_id) DO NOTHING;
