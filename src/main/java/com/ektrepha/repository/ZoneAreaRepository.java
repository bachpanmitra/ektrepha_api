package com.ektrepha.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ektrepha.model.ZoneArea;

public interface ZoneAreaRepository extends JpaRepository<ZoneArea, Long> {

	List<ZoneArea> findAllByCityIgnoreCaseAndStateIgnoreCaseAndActiveTrue(String city, String state);

	// Nearest-zone-by-centroid via earthdistance (cube/earthdistance extensions, already enabled by
	// migration 006) - the substitute for PostGIS polygon containment; see migration 008 header.
	@Query(value = """
			SELECT * FROM zone_areas
			WHERE is_active = true AND centroid_lat IS NOT NULL AND centroid_lng IS NOT NULL
			ORDER BY earth_distance(ll_to_earth(centroid_lat, centroid_lng), ll_to_earth(:lat, :lng)) ASC
			LIMIT 1
			""", nativeQuery = true)
	Optional<ZoneArea> findNearestByCoordinates(@Param("lat") double lat, @Param("lng") double lng);

	// Backs the public "where we're live" marketing endpoint - any zone with at least one LIVE
	// service type rollout row, regardless of which service.
	@Query("""
			SELECT DISTINCT sst.zoneArea FROM ServiceabilityServiceType sst
			WHERE sst.zoneArea.active = true AND sst.status = com.ektrepha.model.ServiceabilityStatus.LIVE
			ORDER BY sst.zoneArea.city ASC, sst.zoneArea.name ASC
			""")
	List<ZoneArea> findAllWithLiveService();

	// Backs the locality typeahead search box - a prefix match ("White...") ranks above a
	// mid-string match ("...ield") the way every "select your area" search box behaves, so typing
	// the start of a name reliably surfaces it first even when a substring match exists elsewhere.
	@Query("""
			SELECT za FROM ZoneArea za
			WHERE za.active = true AND LOWER(za.name) LIKE LOWER(CONCAT('%', :query, '%'))
			ORDER BY CASE WHEN LOWER(za.name) LIKE LOWER(CONCAT(:query, '%')) THEN 0 ELSE 1 END, za.name ASC
			""")
	List<ZoneArea> searchByNameFuzzy(@Param("query") String query, Pageable pageable);

}
