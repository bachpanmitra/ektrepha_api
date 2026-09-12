package com.ektrepha.repository;

import java.util.List;
import java.util.Optional;

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

}
