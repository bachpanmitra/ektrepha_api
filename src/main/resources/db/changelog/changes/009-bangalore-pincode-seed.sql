--liquibase formatted sql

--changeset bachpanmitra:9
-- Real serviceability reference data for Bangalore, derived from the India Post pincode
-- directory (officename/pincode/officeType/divisionname/... - the standard India Post PIN code
-- CSV format). Safe to run in every environment (dev, stage, prod alike) - this is static
-- geography, not demo/test data, and this changeset (like every other) applies exactly once via
-- Liquibase's changelog tracking.
--
-- One zone per India Post "division" (Bangalore East/GPO/South/West) rather than one per post
-- office - a per-office zone would be far too fine-grained for pricing/rollout purposes, and
-- division is the natural existing grouping in the source data.
--
-- The source data has 251 post-office rows but only 105 distinct pincodes (many offices share a
-- pincode). Two pincodes (560001, 560002) appear under two divisions each in the raw data - for
-- those, the division of the Head Office (H.O.) for that pincode wins, since the H.O. is the
-- authoritative office for its pincode; where no H.O. is present for a conflicting pincode, the
-- first division encountered wins. Zone ids are resolved by name (not hardcoded), so this applies
-- cleanly regardless of what zone ids already exist in a given database.
--
-- Both inserts are ON CONFLICT DO NOTHING: a database this runs against may already have real
-- admin-entered zones/pincodes (e.g. an operator manually onboarded a zone before this reference
-- load ran) - this seed must defer to whatever's already there, not fail the whole migration or
-- clobber it. zone_areas.name has no natural uniqueness guarantee elsewhere in the schema, so
-- this migration adds one - safe here since no existing rows collide on name.

ALTER TABLE zone_areas ADD CONSTRAINT uq_zone_areas_name UNIQUE (name);

INSERT INTO zone_areas (name, city, state, centroid_lat, centroid_lng) VALUES
('Bangalore East', 'Bangalore', 'Karnataka', 12.9784, 77.6408),
('Bangalore GPO', 'Bangalore', 'Karnataka', 12.9767, 77.5993),
('Bangalore South', 'Bangalore', 'Karnataka', 12.925, 77.5938),
('Bangalore West', 'Bangalore', 'Karnataka', 13.009, 77.555)
ON CONFLICT (name) DO NOTHING;

INSERT INTO serviceability_pincode (pincode, zone_area_id, is_serviceable, status) VALUES
('560001', (SELECT id FROM zone_areas WHERE name = 'Bangalore GPO'), true, 'live'),
('560002', (SELECT id FROM zone_areas WHERE name = 'Bangalore South'), true, 'live'),
('560003', (SELECT id FROM zone_areas WHERE name = 'Bangalore West'), true, 'live'),
('560004', (SELECT id FROM zone_areas WHERE name = 'Bangalore South'), true, 'live'),
('560005', (SELECT id FROM zone_areas WHERE name = 'Bangalore East'), true, 'live'),
('560006', (SELECT id FROM zone_areas WHERE name = 'Bangalore East'), true, 'live'),
('560007', (SELECT id FROM zone_areas WHERE name = 'Bangalore East'), true, 'live'),
('560008', (SELECT id FROM zone_areas WHERE name = 'Bangalore East'), true, 'live'),
('560009', (SELECT id FROM zone_areas WHERE name = 'Bangalore West'), true, 'live'),
('560010', (SELECT id FROM zone_areas WHERE name = 'Bangalore West'), true, 'live'),
('560011', (SELECT id FROM zone_areas WHERE name = 'Bangalore South'), true, 'live'),
('560012', (SELECT id FROM zone_areas WHERE name = 'Bangalore West'), true, 'live'),
('560013', (SELECT id FROM zone_areas WHERE name = 'Bangalore West'), true, 'live'),
('560014', (SELECT id FROM zone_areas WHERE name = 'Bangalore West'), true, 'live'),
('560015', (SELECT id FROM zone_areas WHERE name = 'Bangalore West'), true, 'live'),
('560016', (SELECT id FROM zone_areas WHERE name = 'Bangalore East'), true, 'live'),
('560017', (SELECT id FROM zone_areas WHERE name = 'Bangalore East'), true, 'live'),
('560018', (SELECT id FROM zone_areas WHERE name = 'Bangalore South'), true, 'live'),
('560019', (SELECT id FROM zone_areas WHERE name = 'Bangalore South'), true, 'live'),
('560020', (SELECT id FROM zone_areas WHERE name = 'Bangalore West'), true, 'live'),
('560021', (SELECT id FROM zone_areas WHERE name = 'Bangalore West'), true, 'live'),
('560022', (SELECT id FROM zone_areas WHERE name = 'Bangalore West'), true, 'live'),
('560023', (SELECT id FROM zone_areas WHERE name = 'Bangalore West'), true, 'live'),
('560024', (SELECT id FROM zone_areas WHERE name = 'Bangalore East'), true, 'live'),
('560025', (SELECT id FROM zone_areas WHERE name = 'Bangalore East'), true, 'live'),
('560026', (SELECT id FROM zone_areas WHERE name = 'Bangalore South'), true, 'live'),
('560027', (SELECT id FROM zone_areas WHERE name = 'Bangalore South'), true, 'live'),
('560029', (SELECT id FROM zone_areas WHERE name = 'Bangalore South'), true, 'live'),
('560030', (SELECT id FROM zone_areas WHERE name = 'Bangalore South'), true, 'live'),
('560032', (SELECT id FROM zone_areas WHERE name = 'Bangalore East'), true, 'live'),
('560033', (SELECT id FROM zone_areas WHERE name = 'Bangalore East'), true, 'live'),
('560034', (SELECT id FROM zone_areas WHERE name = 'Bangalore South'), true, 'live'),
('560035', (SELECT id FROM zone_areas WHERE name = 'Bangalore South'), true, 'live'),
('560036', (SELECT id FROM zone_areas WHERE name = 'Bangalore East'), true, 'live'),
('560037', (SELECT id FROM zone_areas WHERE name = 'Bangalore East'), true, 'live'),
('560038', (SELECT id FROM zone_areas WHERE name = 'Bangalore East'), true, 'live'),
('560039', (SELECT id FROM zone_areas WHERE name = 'Bangalore South'), true, 'live'),
('560040', (SELECT id FROM zone_areas WHERE name = 'Bangalore West'), true, 'live'),
('560041', (SELECT id FROM zone_areas WHERE name = 'Bangalore South'), true, 'live'),
('560042', (SELECT id FROM zone_areas WHERE name = 'Bangalore East'), true, 'live'),
('560043', (SELECT id FROM zone_areas WHERE name = 'Bangalore East'), true, 'live'),
('560045', (SELECT id FROM zone_areas WHERE name = 'Bangalore East'), true, 'live'),
('560046', (SELECT id FROM zone_areas WHERE name = 'Bangalore East'), true, 'live'),
('560047', (SELECT id FROM zone_areas WHERE name = 'Bangalore East'), true, 'live'),
('560048', (SELECT id FROM zone_areas WHERE name = 'Bangalore East'), true, 'live'),
('560049', (SELECT id FROM zone_areas WHERE name = 'Bangalore East'), true, 'live'),
('560050', (SELECT id FROM zone_areas WHERE name = 'Bangalore South'), true, 'live'),
('560051', (SELECT id FROM zone_areas WHERE name = 'Bangalore East'), true, 'live'),
('560053', (SELECT id FROM zone_areas WHERE name = 'Bangalore South'), true, 'live'),
('560054', (SELECT id FROM zone_areas WHERE name = 'Bangalore West'), true, 'live'),
('560055', (SELECT id FROM zone_areas WHERE name = 'Bangalore West'), true, 'live'),
('560056', (SELECT id FROM zone_areas WHERE name = 'Bangalore South'), true, 'live'),
('560057', (SELECT id FROM zone_areas WHERE name = 'Bangalore West'), true, 'live'),
('560058', (SELECT id FROM zone_areas WHERE name = 'Bangalore West'), true, 'live'),
('560059', (SELECT id FROM zone_areas WHERE name = 'Bangalore South'), true, 'live'),
('560060', (SELECT id FROM zone_areas WHERE name = 'Bangalore South'), true, 'live'),
('560061', (SELECT id FROM zone_areas WHERE name = 'Bangalore South'), true, 'live'),
('560062', (SELECT id FROM zone_areas WHERE name = 'Bangalore South'), true, 'live'),
('560063', (SELECT id FROM zone_areas WHERE name = 'Bangalore East'), true, 'live'),
('560064', (SELECT id FROM zone_areas WHERE name = 'Bangalore East'), true, 'live'),
('560065', (SELECT id FROM zone_areas WHERE name = 'Bangalore East'), true, 'live'),
('560066', (SELECT id FROM zone_areas WHERE name = 'Bangalore East'), true, 'live'),
('560067', (SELECT id FROM zone_areas WHERE name = 'Bangalore East'), true, 'live'),
('560068', (SELECT id FROM zone_areas WHERE name = 'Bangalore South'), true, 'live'),
('560070', (SELECT id FROM zone_areas WHERE name = 'Bangalore South'), true, 'live'),
('560071', (SELECT id FROM zone_areas WHERE name = 'Bangalore East'), true, 'live'),
('560072', (SELECT id FROM zone_areas WHERE name = 'Bangalore West'), true, 'live'),
('560073', (SELECT id FROM zone_areas WHERE name = 'Bangalore West'), true, 'live'),
('560074', (SELECT id FROM zone_areas WHERE name = 'Bangalore South'), true, 'live'),
('560075', (SELECT id FROM zone_areas WHERE name = 'Bangalore East'), true, 'live'),
('560076', (SELECT id FROM zone_areas WHERE name = 'Bangalore South'), true, 'live'),
('560077', (SELECT id FROM zone_areas WHERE name = 'Bangalore East'), true, 'live'),
('560078', (SELECT id FROM zone_areas WHERE name = 'Bangalore South'), true, 'live'),
('560079', (SELECT id FROM zone_areas WHERE name = 'Bangalore West'), true, 'live'),
('560080', (SELECT id FROM zone_areas WHERE name = 'Bangalore East'), true, 'live'),
('560081', (SELECT id FROM zone_areas WHERE name = 'Bangalore South'), true, 'live'),
('560082', (SELECT id FROM zone_areas WHERE name = 'Bangalore South'), true, 'live'),
('560083', (SELECT id FROM zone_areas WHERE name = 'Bangalore South'), true, 'live'),
('560084', (SELECT id FROM zone_areas WHERE name = 'Bangalore East'), true, 'live'),
('560085', (SELECT id FROM zone_areas WHERE name = 'Bangalore South'), true, 'live'),
('560086', (SELECT id FROM zone_areas WHERE name = 'Bangalore West'), true, 'live'),
('560087', (SELECT id FROM zone_areas WHERE name = 'Bangalore East'), true, 'live'),
('560088', (SELECT id FROM zone_areas WHERE name = 'Bangalore West'), true, 'live'),
('560089', (SELECT id FROM zone_areas WHERE name = 'Bangalore West'), true, 'live'),
('560090', (SELECT id FROM zone_areas WHERE name = 'Bangalore West'), true, 'live'),
('560091', (SELECT id FROM zone_areas WHERE name = 'Bangalore West'), true, 'live'),
('560092', (SELECT id FROM zone_areas WHERE name = 'Bangalore East'), true, 'live'),
('560093', (SELECT id FROM zone_areas WHERE name = 'Bangalore East'), true, 'live'),
('560094', (SELECT id FROM zone_areas WHERE name = 'Bangalore East'), true, 'live'),
('560095', (SELECT id FROM zone_areas WHERE name = 'Bangalore South'), true, 'live'),
('560096', (SELECT id FROM zone_areas WHERE name = 'Bangalore West'), true, 'live'),
('560097', (SELECT id FROM zone_areas WHERE name = 'Bangalore West'), true, 'live'),
('560098', (SELECT id FROM zone_areas WHERE name = 'Bangalore South'), true, 'live'),
('560099', (SELECT id FROM zone_areas WHERE name = 'Bangalore South'), true, 'live'),
('560100', (SELECT id FROM zone_areas WHERE name = 'Bangalore South'), true, 'live'),
('560102', (SELECT id FROM zone_areas WHERE name = 'Bangalore South'), true, 'live'),
('560103', (SELECT id FROM zone_areas WHERE name = 'Bangalore East'), true, 'live'),
('560104', (SELECT id FROM zone_areas WHERE name = 'Bangalore West'), true, 'live'),
('560105', (SELECT id FROM zone_areas WHERE name = 'Bangalore South'), true, 'live'),
('560107', (SELECT id FROM zone_areas WHERE name = 'Bangalore West'), true, 'live'),
('560108', (SELECT id FROM zone_areas WHERE name = 'Bangalore South'), true, 'live'),
('560109', (SELECT id FROM zone_areas WHERE name = 'Bangalore South'), true, 'live'),
('560110', (SELECT id FROM zone_areas WHERE name = 'Bangalore South'), true, 'live'),
('560112', (SELECT id FROM zone_areas WHERE name = 'Bangalore West'), true, 'live'),
('560300', (SELECT id FROM zone_areas WHERE name = 'Bangalore East'), true, 'live')
ON CONFLICT (pincode) DO NOTHING;
