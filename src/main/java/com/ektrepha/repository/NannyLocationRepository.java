package com.ektrepha.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.ektrepha.model.NannyLocation;

public interface NannyLocationRepository extends JpaRepository<NannyLocation, Long> {

	Optional<NannyLocation> findByNannyId(Long nannyId);

	// One row per nanny — ON CONFLICT keeps the upsert atomic under concurrent pings, matching
	// the spec's own "Upsert nanny_location ON CONFLICT (nanny_id) DO UPDATE" step.
	@Modifying(clearAutomatically = true)
	@Transactional
	@Query(value = """
			INSERT INTO nanny_location (nanny_id, booking_id, lat, lng, updated_at)
			VALUES (:nannyId, :bookingId, :lat, :lng, now())
			ON CONFLICT (nanny_id) DO UPDATE SET booking_id = :bookingId, lat = :lat, lng = :lng, updated_at = now()
			""", nativeQuery = true)
	void upsert(@Param("nannyId") Long nannyId, @Param("bookingId") Long bookingId, @Param("lat") double lat, @Param("lng") double lng);

}
