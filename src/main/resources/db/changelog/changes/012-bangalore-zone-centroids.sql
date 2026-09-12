--liquibase formatted sql

--changeset bachpanmitra:12
-- Backfills centroid_lat/centroid_lng for the 104 locality zones seeded in changeset 11, sourced
-- from a per-post-office India Post geocoding dataset (avinashcelestine/Pincodes-data on GitHub,
-- itself built from the same India Post directory these zones come from) joined by exact office
-- name against each zone's anchor office. This is what makes coordinate-based
-- /serviceability/search actually match these zones - changeset 11 deliberately left them NULL
-- rather than fabricate numbers.
--
-- Each UPDATE is guarded by "AND centroid_lat IS NULL", so this never overwrites a coordinate an
-- admin has already set by hand (Indiranagar, seeded manually before changeset 9, is unaffected by
-- this migration for exactly that reason - it already has one).
--
-- 103 of the 104 zones matched exactly by name. "Udaypura" did not appear in the source dataset at
-- all (only its branch offices did, with inconsistent, likely-fallback coordinates among
-- themselves) and no reliable coordinate for it could be found - it stays NULL. Set it manually via
-- PUT /admin/zones/{id} if/when a real one is available.
--
-- Data-quality note, not fixed here: 4 pairs/groups of zones came back with identical coordinates
-- from the source dataset - a strong signal that at least one office in each group is a
-- geocoding-fallback value (e.g. a generic city-centroid), not that office's real location. Two are
-- plausibly fine (Malleswaram / Malleswaram West are genuinely adjacent); two are not (H.A.L II
-- Stage and Jayangar III Block are on opposite sides of the city and cannot really share a point;
-- Bangalore City / Bangalore G.P.O. / Doorvaninagar / Governmemnt Electric Factory all sharing the
-- exact central-Bangalore point is the same pattern). Worth spot-checking and correcting these four
-- via the admin API before relying on coordinate search near them.

UPDATE zone_areas SET centroid_lat = 13.100841, centroid_lng = 77.594573 WHERE name = 'A F Station Yelahanka' AND centroid_lat IS NULL;
UPDATE zone_areas SET centroid_lat = 13.284699, centroid_lng = 77.607786 WHERE name = 'Achitnagar' AND centroid_lat IS NULL;
UPDATE zone_areas SET centroid_lat = 12.944133, centroid_lng = 77.607615 WHERE name = 'Adugodi' AND centroid_lat IS NULL;
UPDATE zone_areas SET centroid_lat = 12.964357, centroid_lng = 77.621107 WHERE name = 'Agram' AND centroid_lat IS NULL;
UPDATE zone_areas SET centroid_lat = 12.861796, centroid_lng = 77.560655 WHERE name = 'Anjanapura' AND centroid_lat IS NULL;
UPDATE zone_areas SET centroid_lat = 13.030516, centroid_lng = 77.621127 WHERE name = 'Arabic College' AND centroid_lat IS NULL;
UPDATE zone_areas SET centroid_lat = 12.924871, centroid_lng = 77.566241 WHERE name = 'B Sk II Stage' AND centroid_lat IS NULL;
UPDATE zone_areas SET centroid_lat = 12.934527, centroid_lng = 77.543522 WHERE name = 'Banashankari III Stage' AND centroid_lat IS NULL;
UPDATE zone_areas SET centroid_lat = 12.941607, centroid_lng = 77.557853 WHERE name = 'Banashankari' AND centroid_lat IS NULL;
UPDATE zone_areas SET centroid_lat = 12.971599, centroid_lng = 77.594563 WHERE name = 'Bangalore City' AND centroid_lat IS NULL;
UPDATE zone_areas SET centroid_lat = 12.971599, centroid_lng = 77.594563 WHERE name = 'Bangalore G.P.O.' AND centroid_lat IS NULL;
UPDATE zone_areas SET centroid_lat = 13.204492, centroid_lng = 77.707691 WHERE name = 'Bangalore International Airport' AND centroid_lat IS NULL;
UPDATE zone_areas SET centroid_lat = 12.894655, centroid_lng = 77.59882 WHERE name = 'Bannerghatta Road' AND centroid_lat IS NULL;
UPDATE zone_areas SET centroid_lat = 12.805246, centroid_lng = 77.57878 WHERE name = 'Bannerghatta' AND centroid_lat IS NULL;
UPDATE zone_areas SET centroid_lat = 12.942117, centroid_lng = 77.575361 WHERE name = 'Basavanagudi' AND centroid_lat IS NULL;
UPDATE zone_areas SET centroid_lat = 12.963885, centroid_lng = 77.522385 WHERE name = 'Basaveshwaranagar' AND centroid_lat IS NULL;
UPDATE zone_areas SET centroid_lat = 12.933704, centroid_lng = 77.662203 WHERE name = 'Bellandur' AND centroid_lat IS NULL;
UPDATE zone_areas SET centroid_lat = 13.004155, centroid_lng = 77.604592 WHERE name = 'Benson Town' AND centroid_lat IS NULL;
UPDATE zone_areas SET centroid_lat = 13.027698, centroid_lng = 77.558515 WHERE name = 'Bnagalore Viswavidalaya' AND centroid_lat IS NULL;
UPDATE zone_areas SET centroid_lat = 12.89836, centroid_lng = 77.617947 WHERE name = 'Bommanahalli' AND centroid_lat IS NULL;
UPDATE zone_areas SET centroid_lat = 12.815921, centroid_lng = 77.679381 WHERE name = 'Bommasandra Industrial Estate' AND centroid_lat IS NULL;
UPDATE zone_areas SET centroid_lat = 12.985454, centroid_lng = 77.663925 WHERE name = 'C.V.Raman Nagar' AND centroid_lat IS NULL;
UPDATE zone_areas SET centroid_lat = 12.906153, centroid_lng = 77.706575 WHERE name = 'Carmelram' AND centroid_lat IS NULL;
UPDATE zone_areas SET centroid_lat = 12.95701, centroid_lng = 77.563441 WHERE name = 'Chamrajpet' AND centroid_lat IS NULL;
UPDATE zone_areas SET centroid_lat = 12.800495, centroid_lng = 77.713612 WHERE name = 'Chandapura' AND centroid_lat IS NULL;
UPDATE zone_areas SET centroid_lat = 12.970923, centroid_lng = 77.576314 WHERE name = 'Chickpet' AND centroid_lat IS NULL;
UPDATE zone_areas SET centroid_lat = 13.284699, centroid_lng = 77.607786 WHERE name = 'Chikkabanavara' AND centroid_lat IS NULL;
UPDATE zone_areas SET centroid_lat = 12.934665, centroid_lng = 77.605179 WHERE name = 'Dharmaram College' AND centroid_lat IS NULL;
UPDATE zone_areas SET centroid_lat = 12.880662, centroid_lng = 77.55758 WHERE name = 'Doddakallasandra' AND centroid_lat IS NULL;
UPDATE zone_areas SET centroid_lat = 12.960986, centroid_lng = 77.638732 WHERE name = 'Domlur' AND centroid_lat IS NULL;
UPDATE zone_areas SET centroid_lat = 12.971599, centroid_lng = 77.594563 WHERE name = 'Doorvaninagar' AND centroid_lat IS NULL;
UPDATE zone_areas SET centroid_lat = 13.068168, centroid_lng = 77.628963 WHERE name = 'Dr. Shivarama Karanth Nagar' AND centroid_lat IS NULL;
UPDATE zone_areas SET centroid_lat = 12.839939, centroid_lng = 77.677003 WHERE name = 'Electronics City' AND centroid_lat IS NULL;
UPDATE zone_areas SET centroid_lat = 12.99715, centroid_lng = 77.614256 WHERE name = 'Fraser Town' AND centroid_lat IS NULL;
UPDATE zone_areas SET centroid_lat = 13.09843, centroid_lng = 77.581832 WHERE name = 'G.K.V.K.' AND centroid_lat IS NULL;
UPDATE zone_areas SET centroid_lat = 12.947723, centroid_lng = 77.556927 WHERE name = 'Gaviopuram Extension' AND centroid_lat IS NULL;
UPDATE zone_areas SET centroid_lat = 12.971599, centroid_lng = 77.594563 WHERE name = 'Governmemnt Electric Factory' AND centroid_lat IS NULL;
UPDATE zone_areas SET centroid_lat = 13.027599, centroid_lng = 77.584513 WHERE name = 'H.A. Farm' AND centroid_lat IS NULL;
UPDATE zone_areas SET centroid_lat = 12.884478, centroid_lng = 77.617078 WHERE name = 'H.A.L II Stage' AND centroid_lat IS NULL;
UPDATE zone_areas SET centroid_lat = 13.121167, centroid_lng = 77.60788 WHERE name = 'H.K.P. Road' AND centroid_lat IS NULL;
UPDATE zone_areas SET centroid_lat = 12.908136, centroid_lng = 77.647608 WHERE name = 'HSR Layout' AND centroid_lat IS NULL;
UPDATE zone_areas SET centroid_lat = 12.962381, centroid_lng = 77.536558 WHERE name = 'Hampinagar' AND centroid_lat IS NULL;
UPDATE zone_areas SET centroid_lat = 13.15, centroid_lng = 77.49 WHERE name = 'Hessarghatta Lake' AND centroid_lat IS NULL;
UPDATE zone_areas SET centroid_lat = 13.13778, centroid_lng = 77.479127 WHERE name = 'Hessarghatta' AND centroid_lat IS NULL;
UPDATE zone_areas SET centroid_lat = 12.910491, centroid_lng = 77.585717 WHERE name = 'J P Nagar' AND centroid_lat IS NULL;
UPDATE zone_areas SET centroid_lat = 13.008182, centroid_lng = 77.594895 WHERE name = 'J.C.Nagar' AND centroid_lat IS NULL;
UPDATE zone_areas SET centroid_lat = 13.069923, centroid_lng = 77.54486 WHERE name = 'Jalahalli East' AND centroid_lat IS NULL;
UPDATE zone_areas SET centroid_lat = 13.052765, centroid_lng = 77.541899 WHERE name = 'Jalahalli' AND centroid_lat IS NULL;
UPDATE zone_areas SET centroid_lat = 13.06122, centroid_lng = 77.519266 WHERE name = 'Jalahalli West' AND centroid_lat IS NULL;
UPDATE zone_areas SET centroid_lat = 12.925007, centroid_lng = 77.593803 WHERE name = 'Jayanagar' AND centroid_lat IS NULL;
UPDATE zone_areas SET centroid_lat = 12.884478, centroid_lng = 77.617078 WHERE name = 'Jayangar III Block' AND centroid_lat IS NULL;
UPDATE zone_areas SET centroid_lat = 12.784389, centroid_lng = 77.641859 WHERE name = 'Jigani' AND centroid_lat IS NULL;
UPDATE zone_areas SET centroid_lat = 12.974292, centroid_lng = 77.578213 WHERE name = 'K. G. Road' AND centroid_lat IS NULL;
UPDATE zone_areas SET centroid_lat = 12.99084, centroid_lng = 77.760781 WHERE name = 'Kadugodi' AND centroid_lat IS NULL;
UPDATE zone_areas SET centroid_lat = 13.019145, centroid_lng = 77.646453 WHERE name = 'Kalyananagar' AND centroid_lat IS NULL;
UPDATE zone_areas SET centroid_lat = 12.962319, centroid_lng = 77.415111 WHERE name = 'Kengeri' AND centroid_lat IS NULL;
UPDATE zone_areas SET centroid_lat = 13.069521, centroid_lng = 77.582284 WHERE name = 'Kodigehalli' AND centroid_lat IS NULL;
UPDATE zone_areas SET centroid_lat = 12.927923, centroid_lng = 77.627108 WHERE name = 'Koramangala' AND centroid_lat IS NULL;
UPDATE zone_areas SET centroid_lat = 12.928053, centroid_lng = 77.622782 WHERE name = 'Koramangala VI Bk' AND centroid_lat IS NULL;
UPDATE zone_areas SET centroid_lat = 13.00062, centroid_lng = 77.67464 WHERE name = 'Krishnarajapuram' AND centroid_lat IS NULL;
UPDATE zone_areas SET centroid_lat = 12.878061, centroid_lng = 77.444381 WHERE name = 'Kumbalagodu' AND centroid_lat IS NULL;
UPDATE zone_areas SET centroid_lat = 12.986755, centroid_lng = 77.47655 WHERE name = 'Magadi Road' AND centroid_lat IS NULL;
UPDATE zone_areas SET centroid_lat = 12.99128, centroid_lng = 77.687367 WHERE name = 'Mahadevapura' AND centroid_lat IS NULL;
UPDATE zone_areas SET centroid_lat = 13.014594, centroid_lng = 77.551405 WHERE name = 'Mahalakshmipuram Layout' AND centroid_lat IS NULL;
UPDATE zone_areas SET centroid_lat = 13.003062, centroid_lng = 77.564293 WHERE name = 'Malleswaram' AND centroid_lat IS NULL;
UPDATE zone_areas SET centroid_lat = 13.003062, centroid_lng = 77.564293 WHERE name = 'Malleswaram West' AND centroid_lat IS NULL;
UPDATE zone_areas SET centroid_lat = 12.915257, centroid_lng = 77.58583 WHERE name = 'Marathahalli Colony' AND centroid_lat IS NULL;
UPDATE zone_areas SET centroid_lat = 13.001913, centroid_lng = 77.632214 WHERE name = 'Maruthi Sevanagar' AND centroid_lat IS NULL;
UPDATE zone_areas SET centroid_lat = 13.030862, centroid_lng = 77.564684 WHERE name = 'Msrit' AND centroid_lat IS NULL;
UPDATE zone_areas SET centroid_lat = 12.972244, centroid_lng = 77.604324 WHERE name = 'Museum Road' AND centroid_lat IS NULL;
UPDATE zone_areas SET centroid_lat = 12.959945, centroid_lng = 77.508283 WHERE name = 'Nagarbhavi' AND centroid_lat IS NULL;
UPDATE zone_areas SET centroid_lat = 13.043164, centroid_lng = 77.500309 WHERE name = 'Nagasandra' AND centroid_lat IS NULL;
UPDATE zone_areas SET centroid_lat = 13.015991, centroid_lng = 77.533823 WHERE name = 'Nandinilayout' AND centroid_lat IS NULL;
UPDATE zone_areas SET centroid_lat = 12.941145, centroid_lng = 77.52477 WHERE name = 'Nayandahalli' AND centroid_lat IS NULL;
UPDATE zone_areas SET centroid_lat = 12.971757, centroid_lng = 77.655193 WHERE name = 'New Thippasandra' AND centroid_lat IS NULL;
UPDATE zone_areas SET centroid_lat = 13.035339, centroid_lng = 77.528263 WHERE name = 'Peenya Dasarahalli' AND centroid_lat IS NULL;
UPDATE zone_areas SET centroid_lat = 12.97785, centroid_lng = 77.55199 WHERE name = 'Peenya Small Industries' AND centroid_lat IS NULL;
UPDATE zone_areas SET centroid_lat = 13.019568, centroid_lng = 77.596813 WHERE name = 'R T Nagar' AND centroid_lat IS NULL;
UPDATE zone_areas SET centroid_lat = 13.042217, centroid_lng = 77.570044 WHERE name = 'R.M.V. Extension II Stage' AND centroid_lat IS NULL;
UPDATE zone_areas SET centroid_lat = 12.990058, centroid_lng = 77.552492 WHERE name = 'Rajajinagar' AND centroid_lat IS NULL;
UPDATE zone_areas SET centroid_lat = 12.92422, centroid_lng = 77.519119 WHERE name = 'Rajarajeshwarinagar' AND centroid_lat IS NULL;
UPDATE zone_areas SET centroid_lat = 12.953867, centroid_lng = 77.579739 WHERE name = 'Rv Niketan' AND centroid_lat IS NULL;
UPDATE zone_areas SET centroid_lat = 13.006818, centroid_lng = 77.581285 WHERE name = 'Sadashivanagar' AND centroid_lat IS NULL;
UPDATE zone_areas SET centroid_lat = 13.062343, centroid_lng = 77.587103 WHERE name = 'Sahakaranagar P.O' AND centroid_lat IS NULL;
UPDATE zone_areas SET centroid_lat = 13.133961, centroid_lng = 77.492423 WHERE name = 'Science Institute' AND centroid_lat IS NULL;
UPDATE zone_areas SET centroid_lat = 12.993533, centroid_lng = 77.57874 WHERE name = 'Seshadripuram' AND centroid_lat IS NULL;
UPDATE zone_areas SET centroid_lat = 12.980867, centroid_lng = 77.615524 WHERE name = 'Sivan Chetty Gardens' AND centroid_lat IS NULL;
UPDATE zone_areas SET centroid_lat = 12.997016, centroid_lng = 77.568893 WHERE name = 'Srirampuram' AND centroid_lat IS NULL;
UPDATE zone_areas SET centroid_lat = 13.004512, centroid_lng = 77.621393 WHERE name = 'St. Thomas Town' AND centroid_lat IS NULL;
UPDATE zone_areas SET centroid_lat = 12.896452, centroid_lng = 77.540672 WHERE name = 'Subramanyapura' AND centroid_lat IS NULL;
UPDATE zone_areas SET centroid_lat = 12.869423, centroid_lng = 77.53684 WHERE name = 'Thalaghattapura' AND centroid_lat IS NULL;
UPDATE zone_areas SET centroid_lat = 12.96235, centroid_lng = 77.475009 WHERE name = 'Ullalu Upanagar' AND centroid_lat IS NULL;
UPDATE zone_areas SET centroid_lat = 12.938879, centroid_lng = 77.741205 WHERE name = 'Vartur' AND centroid_lat IS NULL;
UPDATE zone_areas SET centroid_lat = 13.085791, centroid_lng = 77.556098 WHERE name = 'Vidyaranyapura' AND centroid_lat IS NULL;
UPDATE zone_areas SET centroid_lat = 12.971916, centroid_lng = 77.529886 WHERE name = 'Vijayanagar' AND centroid_lat IS NULL;
UPDATE zone_areas SET centroid_lat = 12.968232, centroid_lng = 77.671597 WHERE name = 'Vimanapura' AND centroid_lat IS NULL;
UPDATE zone_areas SET centroid_lat = 13.044675, centroid_lng = 77.736624 WHERE name = 'Virgonagar' AND centroid_lat IS NULL;
UPDATE zone_areas SET centroid_lat = 12.996911, centroid_lng = 77.495524 WHERE name = 'Viswaneedam' AND centroid_lat IS NULL;
UPDATE zone_areas SET centroid_lat = 12.95839, centroid_lng = 77.617432 WHERE name = 'Viveknagar' AND centroid_lat IS NULL;
UPDATE zone_areas SET centroid_lat = 12.9698, centroid_lng = 77.749947 WHERE name = 'Whitefield' AND centroid_lat IS NULL;
UPDATE zone_areas SET centroid_lat = 12.948193, centroid_lng = 77.597187 WHERE name = 'Wilson Garden' AND centroid_lat IS NULL;
UPDATE zone_areas SET centroid_lat = 13.094454, centroid_lng = 77.586012 WHERE name = 'Yelahanka' AND centroid_lat IS NULL;
UPDATE zone_areas SET centroid_lat = 13.027966, centroid_lng = 77.540916 WHERE name = 'Yeswanthpura' AND centroid_lat IS NULL;
