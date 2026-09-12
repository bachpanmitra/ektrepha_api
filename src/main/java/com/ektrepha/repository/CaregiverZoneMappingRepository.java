package com.ektrepha.repository;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ektrepha.model.CaregiverZoneMapping;

public interface CaregiverZoneMappingRepository extends JpaRepository<CaregiverZoneMapping, Long> {

	Optional<CaregiverZoneMapping> findByCaregiverIdAndZoneAreaIdAndServiceTypeIdAndActiveTrue(Long caregiverId, Long zoneAreaId, Long serviceTypeId);

	// "active + not currently booked" (design doc's own comment on available_caregivers) - a
	// caregiver mapped to this zone/service who is mid-booking right now must not count as
	// available, or the demand ratio would understate real scarcity.
	@Query("""
			SELECT COUNT(czm) FROM CaregiverZoneMapping czm
			WHERE czm.zoneArea.id = :zoneAreaId AND czm.serviceType.id = :serviceTypeId AND czm.active = true
			  AND NOT EXISTS (
			      SELECT 1 FROM Booking b
			      WHERE b.nanny.id = czm.caregiver.id
			        AND b.status IN (com.ektrepha.model.BookingStatus.CONFIRMED, com.ektrepha.model.BookingStatus.IN_PROGRESS)
			        AND :now BETWEEN b.startTime AND b.endTime
			  )
			""")
	int countAvailableNow(@Param("zoneAreaId") Long zoneAreaId, @Param("serviceTypeId") Long serviceTypeId, @Param("now") Instant now);

}
