--liquibase formatted sql

--changeset bachpanmitra:11
-- Replaces the 4 coarse India-Post-division zones (Bangalore East/GPO/South/West, seeded in
-- changeset 9) with one zone per real named locality - Whitefield, Mahadevapura, Marathahalli,
-- Koramangala, Indiranagar-style granularity, not "Bangalore East" spanning HAL to Yelahanka to
-- Whitefield under one price. A division that broad is useless as a pricing zone.
--
-- Zone = one per India Post delivery office (S.O./H.O.), named after the office with its office-
-- type suffix stripped (e.g. "Whitefield S.O" -> "Whitefield"). Branch offices (B.O.) don't get
-- their own zone - they roll up into the delivery office they report to (relatedSuboffice, or
-- relatedHeadoffice if no suboffice is given), since a B.O. is a small collection point within its
-- parent office's coverage area, not a distinct addressable locality. In this dataset every B.O.
-- shares its exact pincode with its parent office anyway, so this rollup loses no pincode
-- coverage - every zone here ends up with exactly one pincode.
--
-- Where more than one delivery office shares a single pincode (54 of the 105 pincodes in this
-- dataset do - pincodes are coarser than individual post offices in dense areas), one wins by
-- priority: an H.O. beats any S.O.; a "Delivery"-status S.O. beats a "Non-Delivery" one (that's
-- literally why 560066 becomes "Whitefield", not "EPIP" - both are S.O.s at that pincode, but only
-- Whitefield S.O. is a Delivery office); first-encountered breaks any remaining tie.
--
-- "Indiranagar" is deliberately excluded from every insert below - that zone (and its pincode,
-- 560038) was already created manually with its own real pricing before this migration ran, and
-- must not be touched by it.
--
-- Centroid_lat/lng are left NULL for all 105 new zones: this migration has real names and pincode
-- coverage sourced from the India Post directory, but not verified per-locality coordinates, and
-- seeding 105 fabricated-looking lat/lng pairs would be worse than leaving them empty. Pincode and
-- city+state search work immediately; coordinate-based search won't match these zones until real
-- coordinates are set (via PUT /admin/zones/{id}, or by wiring a real GeocodingProvider - see
-- NoopGeocodingProvider's javadoc for that seam).
--
-- No BEML zone: no post office named BEML (or BEML Layout) appears anywhere in the source India
-- Post dataset this migration was built from, so one isn't fabricated here. "Varthur" is seeded as
-- "Vartur" - the exact spelling India Post uses for that office.

DELETE FROM zone_service_pricing
WHERE zone_area_id IN (SELECT id FROM zone_areas WHERE name IN ('Bangalore East', 'Bangalore GPO', 'Bangalore South', 'Bangalore West'));

DELETE FROM serviceability_service_type
WHERE zone_area_id IN (SELECT id FROM zone_areas WHERE name IN ('Bangalore East', 'Bangalore GPO', 'Bangalore South', 'Bangalore West'));

DELETE FROM serviceability_pincode
WHERE zone_area_id IN (SELECT id FROM zone_areas WHERE name IN ('Bangalore East', 'Bangalore GPO', 'Bangalore South', 'Bangalore West'));

DELETE FROM caregiver_zone_mapping
WHERE zone_area_id IN (SELECT id FROM zone_areas WHERE name IN ('Bangalore East', 'Bangalore GPO', 'Bangalore South', 'Bangalore West'));

DELETE FROM serviceability_waitlist
WHERE zone_area_id IN (SELECT id FROM zone_areas WHERE name IN ('Bangalore East', 'Bangalore GPO', 'Bangalore South', 'Bangalore West'));

DELETE FROM zone_demand_snapshot
WHERE zone_area_id IN (SELECT id FROM zone_areas WHERE name IN ('Bangalore East', 'Bangalore GPO', 'Bangalore South', 'Bangalore West'));

DELETE FROM zone_areas WHERE name IN ('Bangalore East', 'Bangalore GPO', 'Bangalore South', 'Bangalore West');

INSERT INTO zone_areas (name, city, state) VALUES
('A F Station Yelahanka', 'Bangalore', 'Karnataka'),
('Achitnagar', 'Bangalore', 'Karnataka'),
('Adugodi', 'Bangalore', 'Karnataka'),
('Agram', 'Bangalore', 'Karnataka'),
('Anjanapura', 'Bangalore', 'Karnataka'),
('Arabic College', 'Bangalore', 'Karnataka'),
('B Sk II Stage', 'Bangalore', 'Karnataka'),
('Banashankari III Stage', 'Bangalore', 'Karnataka'),
('Banashankari', 'Bangalore', 'Karnataka'),
('Bangalore City', 'Bangalore', 'Karnataka'),
('Bangalore G.P.O.', 'Bangalore', 'Karnataka'),
('Bangalore International Airport', 'Bangalore', 'Karnataka'),
('Bannerghatta Road', 'Bangalore', 'Karnataka'),
('Bannerghatta', 'Bangalore', 'Karnataka'),
('Basavanagudi', 'Bangalore', 'Karnataka'),
('Basaveshwaranagar', 'Bangalore', 'Karnataka'),
('Bellandur', 'Bangalore', 'Karnataka'),
('Benson Town', 'Bangalore', 'Karnataka'),
('Bnagalore Viswavidalaya', 'Bangalore', 'Karnataka'),
('Bommanahalli', 'Bangalore', 'Karnataka'),
('Bommasandra Industrial Estate', 'Bangalore', 'Karnataka'),
('C.V.Raman Nagar', 'Bangalore', 'Karnataka'),
('Carmelram', 'Bangalore', 'Karnataka'),
('Chamrajpet', 'Bangalore', 'Karnataka'),
('Chandapura', 'Bangalore', 'Karnataka'),
('Chickpet', 'Bangalore', 'Karnataka'),
('Chikkabanavara', 'Bangalore', 'Karnataka'),
('Dharmaram College', 'Bangalore', 'Karnataka'),
('Doddakallasandra', 'Bangalore', 'Karnataka'),
('Domlur', 'Bangalore', 'Karnataka'),
('Doorvaninagar', 'Bangalore', 'Karnataka'),
('Dr. Shivarama Karanth Nagar', 'Bangalore', 'Karnataka'),
('Electronics City', 'Bangalore', 'Karnataka'),
('Fraser Town', 'Bangalore', 'Karnataka'),
('G.K.V.K.', 'Bangalore', 'Karnataka'),
('Gaviopuram Extension', 'Bangalore', 'Karnataka'),
('Governmemnt Electric Factory', 'Bangalore', 'Karnataka'),
('H.A. Farm', 'Bangalore', 'Karnataka'),
('H.A.L II Stage', 'Bangalore', 'Karnataka'),
('H.K.P. Road', 'Bangalore', 'Karnataka'),
('HSR Layout', 'Bangalore', 'Karnataka'),
('Hampinagar', 'Bangalore', 'Karnataka'),
('Hessarghatta Lake', 'Bangalore', 'Karnataka'),
('Hessarghatta', 'Bangalore', 'Karnataka'),
('J P Nagar', 'Bangalore', 'Karnataka'),
('J.C.Nagar', 'Bangalore', 'Karnataka'),
('Jalahalli East', 'Bangalore', 'Karnataka'),
('Jalahalli', 'Bangalore', 'Karnataka'),
('Jalahalli West', 'Bangalore', 'Karnataka'),
('Jayanagar', 'Bangalore', 'Karnataka'),
('Jayangar III Block', 'Bangalore', 'Karnataka'),
('Jigani', 'Bangalore', 'Karnataka'),
('K. G. Road', 'Bangalore', 'Karnataka'),
('Kadugodi', 'Bangalore', 'Karnataka'),
('Kalyananagar', 'Bangalore', 'Karnataka'),
('Kengeri', 'Bangalore', 'Karnataka'),
('Kodigehalli', 'Bangalore', 'Karnataka'),
('Koramangala', 'Bangalore', 'Karnataka'),
('Koramangala VI Bk', 'Bangalore', 'Karnataka'),
('Krishnarajapuram', 'Bangalore', 'Karnataka'),
('Kumbalagodu', 'Bangalore', 'Karnataka'),
('Magadi Road', 'Bangalore', 'Karnataka'),
('Mahadevapura', 'Bangalore', 'Karnataka'),
('Mahalakshmipuram Layout', 'Bangalore', 'Karnataka'),
('Malleswaram', 'Bangalore', 'Karnataka'),
('Malleswaram West', 'Bangalore', 'Karnataka'),
('Marathahalli Colony', 'Bangalore', 'Karnataka'),
('Maruthi Sevanagar', 'Bangalore', 'Karnataka'),
('Msrit', 'Bangalore', 'Karnataka'),
('Museum Road', 'Bangalore', 'Karnataka'),
('Nagarbhavi', 'Bangalore', 'Karnataka'),
('Nagasandra', 'Bangalore', 'Karnataka'),
('Nandinilayout', 'Bangalore', 'Karnataka'),
('Nayandahalli', 'Bangalore', 'Karnataka'),
('New Thippasandra', 'Bangalore', 'Karnataka'),
('Peenya Dasarahalli', 'Bangalore', 'Karnataka'),
('Peenya Small Industries', 'Bangalore', 'Karnataka'),
('R T Nagar', 'Bangalore', 'Karnataka'),
('R.M.V. Extension II Stage', 'Bangalore', 'Karnataka'),
('Rajajinagar', 'Bangalore', 'Karnataka'),
('Rajarajeshwarinagar', 'Bangalore', 'Karnataka'),
('Rv Niketan', 'Bangalore', 'Karnataka'),
('Sadashivanagar', 'Bangalore', 'Karnataka'),
('Sahakaranagar P.O', 'Bangalore', 'Karnataka'),
('Science Institute', 'Bangalore', 'Karnataka'),
('Seshadripuram', 'Bangalore', 'Karnataka'),
('Sivan Chetty Gardens', 'Bangalore', 'Karnataka'),
('Srirampuram', 'Bangalore', 'Karnataka'),
('St. Thomas Town', 'Bangalore', 'Karnataka'),
('Subramanyapura', 'Bangalore', 'Karnataka'),
('Thalaghattapura', 'Bangalore', 'Karnataka'),
('Udaypura', 'Bangalore', 'Karnataka'),
('Ullalu Upanagar', 'Bangalore', 'Karnataka'),
('Vartur', 'Bangalore', 'Karnataka'),
('Vidyaranyapura', 'Bangalore', 'Karnataka'),
('Vijayanagar', 'Bangalore', 'Karnataka'),
('Vimanapura', 'Bangalore', 'Karnataka'),
('Virgonagar', 'Bangalore', 'Karnataka'),
('Viswaneedam', 'Bangalore', 'Karnataka'),
('Viveknagar', 'Bangalore', 'Karnataka'),
('Whitefield', 'Bangalore', 'Karnataka'),
('Wilson Garden', 'Bangalore', 'Karnataka'),
('Yelahanka', 'Bangalore', 'Karnataka'),
('Yeswanthpura', 'Bangalore', 'Karnataka')
ON CONFLICT (name) DO NOTHING;

INSERT INTO serviceability_pincode (pincode, zone_area_id, is_serviceable, status) VALUES
('560001', (SELECT id FROM zone_areas WHERE name = 'Bangalore G.P.O.'), true, 'live'),
('560002', (SELECT id FROM zone_areas WHERE name = 'Bangalore City'), true, 'live'),
('560003', (SELECT id FROM zone_areas WHERE name = 'Malleswaram'), true, 'live'),
('560004', (SELECT id FROM zone_areas WHERE name = 'Basavanagudi'), true, 'live'),
('560005', (SELECT id FROM zone_areas WHERE name = 'Fraser Town'), true, 'live'),
('560006', (SELECT id FROM zone_areas WHERE name = 'J.C.Nagar'), true, 'live'),
('560007', (SELECT id FROM zone_areas WHERE name = 'Agram'), true, 'live'),
('560008', (SELECT id FROM zone_areas WHERE name = 'H.A.L II Stage'), true, 'live'),
('560009', (SELECT id FROM zone_areas WHERE name = 'K. G. Road'), true, 'live'),
('560010', (SELECT id FROM zone_areas WHERE name = 'Rajajinagar'), true, 'live'),
('560011', (SELECT id FROM zone_areas WHERE name = 'Jayangar III Block'), true, 'live'),
('560012', (SELECT id FROM zone_areas WHERE name = 'Science Institute'), true, 'live'),
('560013', (SELECT id FROM zone_areas WHERE name = 'Jalahalli'), true, 'live'),
('560014', (SELECT id FROM zone_areas WHERE name = 'Jalahalli East'), true, 'live'),
('560015', (SELECT id FROM zone_areas WHERE name = 'Jalahalli West'), true, 'live'),
('560016', (SELECT id FROM zone_areas WHERE name = 'Doorvaninagar'), true, 'live'),
('560017', (SELECT id FROM zone_areas WHERE name = 'Vimanapura'), true, 'live'),
('560018', (SELECT id FROM zone_areas WHERE name = 'Chamrajpet'), true, 'live'),
('560019', (SELECT id FROM zone_areas WHERE name = 'Gaviopuram Extension'), true, 'live'),
('560020', (SELECT id FROM zone_areas WHERE name = 'Seshadripuram'), true, 'live'),
('560021', (SELECT id FROM zone_areas WHERE name = 'Srirampuram'), true, 'live'),
('560022', (SELECT id FROM zone_areas WHERE name = 'Yeswanthpura'), true, 'live'),
('560023', (SELECT id FROM zone_areas WHERE name = 'Magadi Road'), true, 'live'),
('560024', (SELECT id FROM zone_areas WHERE name = 'H.A. Farm'), true, 'live'),
('560025', (SELECT id FROM zone_areas WHERE name = 'Museum Road'), true, 'live'),
('560026', (SELECT id FROM zone_areas WHERE name = 'Governmemnt Electric Factory'), true, 'live'),
('560027', (SELECT id FROM zone_areas WHERE name = 'Wilson Garden'), true, 'live'),
('560029', (SELECT id FROM zone_areas WHERE name = 'Dharmaram College'), true, 'live'),
('560030', (SELECT id FROM zone_areas WHERE name = 'Adugodi'), true, 'live'),
('560032', (SELECT id FROM zone_areas WHERE name = 'R T Nagar'), true, 'live'),
('560033', (SELECT id FROM zone_areas WHERE name = 'Maruthi Sevanagar'), true, 'live'),
('560034', (SELECT id FROM zone_areas WHERE name = 'Koramangala'), true, 'live'),
('560035', (SELECT id FROM zone_areas WHERE name = 'Carmelram'), true, 'live'),
('560036', (SELECT id FROM zone_areas WHERE name = 'Krishnarajapuram'), true, 'live'),
('560037', (SELECT id FROM zone_areas WHERE name = 'Marathahalli Colony'), true, 'live'),
('560039', (SELECT id FROM zone_areas WHERE name = 'Nayandahalli'), true, 'live'),
('560040', (SELECT id FROM zone_areas WHERE name = 'Vijayanagar'), true, 'live'),
('560041', (SELECT id FROM zone_areas WHERE name = 'Jayanagar'), true, 'live'),
('560042', (SELECT id FROM zone_areas WHERE name = 'Sivan Chetty Gardens'), true, 'live'),
('560043', (SELECT id FROM zone_areas WHERE name = 'Kalyananagar'), true, 'live'),
('560045', (SELECT id FROM zone_areas WHERE name = 'Arabic College'), true, 'live'),
('560046', (SELECT id FROM zone_areas WHERE name = 'Benson Town'), true, 'live'),
('560047', (SELECT id FROM zone_areas WHERE name = 'Viveknagar'), true, 'live'),
('560048', (SELECT id FROM zone_areas WHERE name = 'Mahadevapura'), true, 'live'),
('560049', (SELECT id FROM zone_areas WHERE name = 'Virgonagar'), true, 'live'),
('560050', (SELECT id FROM zone_areas WHERE name = 'Banashankari'), true, 'live'),
('560051', (SELECT id FROM zone_areas WHERE name = 'H.K.P. Road'), true, 'live'),
('560053', (SELECT id FROM zone_areas WHERE name = 'Chickpet'), true, 'live'),
('560054', (SELECT id FROM zone_areas WHERE name = 'Msrit'), true, 'live'),
('560055', (SELECT id FROM zone_areas WHERE name = 'Malleswaram West'), true, 'live'),
('560056', (SELECT id FROM zone_areas WHERE name = 'Bnagalore Viswavidalaya'), true, 'live'),
('560057', (SELECT id FROM zone_areas WHERE name = 'Peenya Dasarahalli'), true, 'live'),
('560058', (SELECT id FROM zone_areas WHERE name = 'Peenya Small Industries'), true, 'live'),
('560059', (SELECT id FROM zone_areas WHERE name = 'Rv Niketan'), true, 'live'),
('560060', (SELECT id FROM zone_areas WHERE name = 'Kengeri'), true, 'live'),
('560061', (SELECT id FROM zone_areas WHERE name = 'Subramanyapura'), true, 'live'),
('560062', (SELECT id FROM zone_areas WHERE name = 'Doddakallasandra'), true, 'live'),
('560063', (SELECT id FROM zone_areas WHERE name = 'A F Station Yelahanka'), true, 'live'),
('560064', (SELECT id FROM zone_areas WHERE name = 'Yelahanka'), true, 'live'),
('560065', (SELECT id FROM zone_areas WHERE name = 'G.K.V.K.'), true, 'live'),
('560066', (SELECT id FROM zone_areas WHERE name = 'Whitefield'), true, 'live'),
('560067', (SELECT id FROM zone_areas WHERE name = 'Kadugodi'), true, 'live'),
('560068', (SELECT id FROM zone_areas WHERE name = 'Bommanahalli'), true, 'live'),
('560070', (SELECT id FROM zone_areas WHERE name = 'B Sk II Stage'), true, 'live'),
('560071', (SELECT id FROM zone_areas WHERE name = 'Domlur'), true, 'live'),
('560072', (SELECT id FROM zone_areas WHERE name = 'Nagarbhavi'), true, 'live'),
('560073', (SELECT id FROM zone_areas WHERE name = 'Nagasandra'), true, 'live'),
('560074', (SELECT id FROM zone_areas WHERE name = 'Kumbalagodu'), true, 'live'),
('560075', (SELECT id FROM zone_areas WHERE name = 'New Thippasandra'), true, 'live'),
('560076', (SELECT id FROM zone_areas WHERE name = 'Bannerghatta Road'), true, 'live'),
('560077', (SELECT id FROM zone_areas WHERE name = 'Dr. Shivarama Karanth Nagar'), true, 'live'),
('560078', (SELECT id FROM zone_areas WHERE name = 'J P Nagar'), true, 'live'),
('560079', (SELECT id FROM zone_areas WHERE name = 'Basaveshwaranagar'), true, 'live'),
('560080', (SELECT id FROM zone_areas WHERE name = 'Sadashivanagar'), true, 'live'),
('560081', (SELECT id FROM zone_areas WHERE name = 'Chandapura'), true, 'live'),
('560082', (SELECT id FROM zone_areas WHERE name = 'Udaypura'), true, 'live'),
('560083', (SELECT id FROM zone_areas WHERE name = 'Bannerghatta'), true, 'live'),
('560084', (SELECT id FROM zone_areas WHERE name = 'St. Thomas Town'), true, 'live'),
('560085', (SELECT id FROM zone_areas WHERE name = 'Banashankari III Stage'), true, 'live'),
('560086', (SELECT id FROM zone_areas WHERE name = 'Mahalakshmipuram Layout'), true, 'live'),
('560087', (SELECT id FROM zone_areas WHERE name = 'Vartur'), true, 'live'),
('560088', (SELECT id FROM zone_areas WHERE name = 'Hessarghatta'), true, 'live'),
('560089', (SELECT id FROM zone_areas WHERE name = 'Hessarghatta Lake'), true, 'live'),
('560090', (SELECT id FROM zone_areas WHERE name = 'Chikkabanavara'), true, 'live'),
('560091', (SELECT id FROM zone_areas WHERE name = 'Viswaneedam'), true, 'live'),
('560092', (SELECT id FROM zone_areas WHERE name = 'Sahakaranagar P.O'), true, 'live'),
('560093', (SELECT id FROM zone_areas WHERE name = 'C.V.Raman Nagar'), true, 'live'),
('560094', (SELECT id FROM zone_areas WHERE name = 'R.M.V. Extension II Stage'), true, 'live'),
('560095', (SELECT id FROM zone_areas WHERE name = 'Koramangala VI Bk'), true, 'live'),
('560096', (SELECT id FROM zone_areas WHERE name = 'Nandinilayout'), true, 'live'),
('560097', (SELECT id FROM zone_areas WHERE name = 'Vidyaranyapura'), true, 'live'),
('560098', (SELECT id FROM zone_areas WHERE name = 'Rajarajeshwarinagar'), true, 'live'),
('560099', (SELECT id FROM zone_areas WHERE name = 'Bommasandra Industrial Estate'), true, 'live'),
('560100', (SELECT id FROM zone_areas WHERE name = 'Electronics City'), true, 'live'),
('560102', (SELECT id FROM zone_areas WHERE name = 'HSR Layout'), true, 'live'),
('560103', (SELECT id FROM zone_areas WHERE name = 'Bellandur'), true, 'live'),
('560104', (SELECT id FROM zone_areas WHERE name = 'Hampinagar'), true, 'live'),
('560105', (SELECT id FROM zone_areas WHERE name = 'Jigani'), true, 'live'),
('560107', (SELECT id FROM zone_areas WHERE name = 'Achitnagar'), true, 'live'),
('560108', (SELECT id FROM zone_areas WHERE name = 'Anjanapura'), true, 'live'),
('560109', (SELECT id FROM zone_areas WHERE name = 'Thalaghattapura'), true, 'live'),
('560110', (SELECT id FROM zone_areas WHERE name = 'Ullalu Upanagar'), true, 'live'),
('560112', (SELECT id FROM zone_areas WHERE name = 'Kodigehalli'), true, 'live'),
('560300', (SELECT id FROM zone_areas WHERE name = 'Bangalore International Airport'), true, 'live')
ON CONFLICT (pincode) DO NOTHING;

INSERT INTO serviceability_service_type (zone_area_id, service_type_id, status, launched_at)
SELECT za.id, st.id, 'live', now()
FROM zone_areas za CROSS JOIN service_types st
WHERE za.name IN ('A F Station Yelahanka', 'Achitnagar', 'Adugodi', 'Agram', 'Anjanapura', 'Arabic College', 'B Sk II Stage', 'Banashankari III Stage', 'Banashankari', 'Bangalore City', 'Bangalore G.P.O.', 'Bangalore International Airport', 'Bannerghatta Road', 'Bannerghatta', 'Basavanagudi', 'Basaveshwaranagar', 'Bellandur', 'Benson Town', 'Bnagalore Viswavidalaya', 'Bommanahalli', 'Bommasandra Industrial Estate', 'C.V.Raman Nagar', 'Carmelram', 'Chamrajpet', 'Chandapura', 'Chickpet', 'Chikkabanavara', 'Dharmaram College', 'Doddakallasandra', 'Domlur', 'Doorvaninagar', 'Dr. Shivarama Karanth Nagar', 'Electronics City', 'Fraser Town', 'G.K.V.K.', 'Gaviopuram Extension', 'Governmemnt Electric Factory', 'H.A. Farm', 'H.A.L II Stage', 'H.K.P. Road', 'HSR Layout', 'Hampinagar', 'Hessarghatta Lake', 'Hessarghatta', 'J P Nagar', 'J.C.Nagar', 'Jalahalli East', 'Jalahalli', 'Jalahalli West', 'Jayanagar', 'Jayangar III Block', 'Jigani', 'K. G. Road', 'Kadugodi', 'Kalyananagar', 'Kengeri', 'Kodigehalli', 'Koramangala', 'Koramangala VI Bk', 'Krishnarajapuram', 'Kumbalagodu', 'Magadi Road', 'Mahadevapura', 'Mahalakshmipuram Layout', 'Malleswaram', 'Malleswaram West', 'Marathahalli Colony', 'Maruthi Sevanagar', 'Msrit', 'Museum Road', 'Nagarbhavi', 'Nagasandra', 'Nandinilayout', 'Nayandahalli', 'New Thippasandra', 'Peenya Dasarahalli', 'Peenya Small Industries', 'R T Nagar', 'R.M.V. Extension II Stage', 'Rajajinagar', 'Rajarajeshwarinagar', 'Rv Niketan', 'Sadashivanagar', 'Sahakaranagar P.O', 'Science Institute', 'Seshadripuram', 'Sivan Chetty Gardens', 'Srirampuram', 'St. Thomas Town', 'Subramanyapura', 'Thalaghattapura', 'Udaypura', 'Ullalu Upanagar', 'Vartur', 'Vidyaranyapura', 'Vijayanagar', 'Vimanapura', 'Virgonagar', 'Viswaneedam', 'Viveknagar', 'Whitefield', 'Wilson Garden', 'Yelahanka', 'Yeswanthpura')
  AND st.code = 'childcare'
ON CONFLICT (zone_area_id, service_type_id) DO NOTHING;

INSERT INTO zone_service_pricing (zone_area_id, service_type_id, pricing_mode, rate_min, rate_max, currency, min_booking_hours, platform_fee_pct)
SELECT za.id, st.id, 'range', 180.00, 320.00, 'INR', 2, 10.00
FROM zone_areas za CROSS JOIN service_types st
WHERE za.name IN ('A F Station Yelahanka', 'Achitnagar', 'Adugodi', 'Agram', 'Anjanapura', 'Arabic College', 'B Sk II Stage', 'Banashankari III Stage', 'Banashankari', 'Bangalore City', 'Bangalore G.P.O.', 'Bangalore International Airport', 'Bannerghatta Road', 'Bannerghatta', 'Basavanagudi', 'Basaveshwaranagar', 'Bellandur', 'Benson Town', 'Bnagalore Viswavidalaya', 'Bommanahalli', 'Bommasandra Industrial Estate', 'C.V.Raman Nagar', 'Carmelram', 'Chamrajpet', 'Chandapura', 'Chickpet', 'Chikkabanavara', 'Dharmaram College', 'Doddakallasandra', 'Domlur', 'Doorvaninagar', 'Dr. Shivarama Karanth Nagar', 'Electronics City', 'Fraser Town', 'G.K.V.K.', 'Gaviopuram Extension', 'Governmemnt Electric Factory', 'H.A. Farm', 'H.A.L II Stage', 'H.K.P. Road', 'HSR Layout', 'Hampinagar', 'Hessarghatta Lake', 'Hessarghatta', 'J P Nagar', 'J.C.Nagar', 'Jalahalli East', 'Jalahalli', 'Jalahalli West', 'Jayanagar', 'Jayangar III Block', 'Jigani', 'K. G. Road', 'Kadugodi', 'Kalyananagar', 'Kengeri', 'Kodigehalli', 'Koramangala', 'Koramangala VI Bk', 'Krishnarajapuram', 'Kumbalagodu', 'Magadi Road', 'Mahadevapura', 'Mahalakshmipuram Layout', 'Malleswaram', 'Malleswaram West', 'Marathahalli Colony', 'Maruthi Sevanagar', 'Msrit', 'Museum Road', 'Nagarbhavi', 'Nagasandra', 'Nandinilayout', 'Nayandahalli', 'New Thippasandra', 'Peenya Dasarahalli', 'Peenya Small Industries', 'R T Nagar', 'R.M.V. Extension II Stage', 'Rajajinagar', 'Rajarajeshwarinagar', 'Rv Niketan', 'Sadashivanagar', 'Sahakaranagar P.O', 'Science Institute', 'Seshadripuram', 'Sivan Chetty Gardens', 'Srirampuram', 'St. Thomas Town', 'Subramanyapura', 'Thalaghattapura', 'Udaypura', 'Ullalu Upanagar', 'Vartur', 'Vidyaranyapura', 'Vijayanagar', 'Vimanapura', 'Virgonagar', 'Viswaneedam', 'Viveknagar', 'Whitefield', 'Wilson Garden', 'Yelahanka', 'Yeswanthpura')
  AND st.code = 'childcare'
ON CONFLICT (zone_area_id, service_type_id) DO NOTHING;
