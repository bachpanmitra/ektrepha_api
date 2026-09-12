package com.ektrepha.repository;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ektrepha.model.Booking;

public interface BookingRepository extends JpaRepository<Booking, Long> {

	Optional<Booking> findByIdAndParentId(Long id, Long parentId);

	// Demand signal for the childcare vertical's dynamic pricing recompute — "open" means still
	// PENDING and requested recently (rolling window), counted against nannies actually mapped to
	// that zone/service via caregiver_zone_mapping, not just any nanny.
	@Query("""
			SELECT COUNT(b) FROM Booking b
			JOIN CaregiverZoneMapping czm ON czm.caregiver.id = b.nanny.id AND czm.active = true
			WHERE czm.zoneArea.id = :zoneAreaId AND czm.serviceType.id = :serviceTypeId
			  AND b.status = com.ektrepha.model.BookingStatus.PENDING AND b.createdAt >= :since
			""")
	int countOpenRequestsForZoneService(@Param("zoneAreaId") Long zoneAreaId, @Param("serviceTypeId") Long serviceTypeId, @Param("since") Instant since);

}
