--liquibase formatted sql

--changeset ektrepha:15
-- Placeholder serviceability for Gurugram, Patna, and Noida — one zone per city (not
-- locality-level, unlike the 105-zone Bangalore dataset in changeset 11), so these cities show up
-- as live in the UI without waiting on a real locality/pricing survey per city. Pincodes are each
-- city's real, well-known central/GPO pincode (Gurugram Sector 14/Civil Lines 122001, Patna GPO
-- 800001, Noida Sector 15/GPO 201301) — genuine, not fabricated — but coverage is deliberately
-- coarse: a resident of another part of any of these cities won't yet match a pincode search.
-- Pricing mirrors the Bangalore reference range from changeset 10 (not city-specific market
-- data) purely so /pricing/calculate has something to return; retune via the admin pricing API
-- once real per-city numbers are known. Only childcare is marked live, matching Bangalore's
-- rollout pattern — every other service type stays at its default NOT_PLANNED until deliberately
-- rolled out here too.

INSERT INTO zone_areas (name, city, state) VALUES
('Gurugram Central', 'Gurugram', 'Haryana'),
('Patna Central', 'Patna', 'Bihar'),
('Noida Central', 'Noida', 'Uttar Pradesh')
ON CONFLICT (name) DO NOTHING;

INSERT INTO serviceability_pincode (pincode, zone_area_id, is_serviceable, status) VALUES
('122001', (SELECT id FROM zone_areas WHERE name = 'Gurugram Central'), true, 'live'),
('800001', (SELECT id FROM zone_areas WHERE name = 'Patna Central'), true, 'live'),
('201301', (SELECT id FROM zone_areas WHERE name = 'Noida Central'), true, 'live')
ON CONFLICT (pincode) DO NOTHING;

INSERT INTO serviceability_service_type (zone_area_id, service_type_id, status, launched_at)
SELECT za.id, st.id, 'live', now()
FROM zone_areas za
CROSS JOIN service_types st
WHERE za.name IN ('Gurugram Central', 'Patna Central', 'Noida Central')
  AND st.code = 'childcare'
ON CONFLICT (zone_area_id, service_type_id) DO NOTHING;

INSERT INTO zone_service_pricing (zone_area_id, service_type_id, pricing_mode, rate_min, rate_max, currency, min_booking_hours, platform_fee_pct)
SELECT za.id, st.id, 'range', 180.00, 320.00, 'INR', 2, 10.00
FROM zone_areas za
CROSS JOIN service_types st
WHERE za.name IN ('Gurugram Central', 'Patna Central', 'Noida Central')
  AND st.code = 'childcare'
ON CONFLICT (zone_area_id, service_type_id) DO NOTHING;
