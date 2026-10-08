package com.ektrepha.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ektrepha.model.ServiceabilityPincode;
import com.ektrepha.model.ServiceabilityStatus;

public interface ServiceabilityPincodeRepository extends JpaRepository<ServiceabilityPincode, Long> {

	Optional<ServiceabilityPincode> findByPincode(String pincode);

	boolean existsByPincode(String pincode);

	// Admin Zones & Pricing screen's per-zone pincode table.
	List<ServiceabilityPincode> findAllByZoneAreaIdOrderByPincodeAsc(Long zoneAreaId);

	// Backs the standalone admin "All Pincodes" screen — every pincode across every zone,
	// optionally filtered by a pincode/zone-name search term and/or status.
	@Query("""
			SELECT p FROM ServiceabilityPincode p
			WHERE (:likeQ IS NULL OR LOWER(p.pincode) LIKE :likeQ OR LOWER(p.zoneArea.name) LIKE :likeQ)
			AND (:status IS NULL OR p.status = :status)
			ORDER BY p.pincode ASC
			""")
	Page<ServiceabilityPincode> search(@Param("likeQ") String likeQ, @Param("status") ServiceabilityStatus status, Pageable pageable);

}
