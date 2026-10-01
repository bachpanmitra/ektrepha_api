package com.ektrepha.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ektrepha.model.Nanny;
import com.ektrepha.model.NannyVerificationStatus;

public interface NannyRepository extends JpaRepository<Nanny, Long> {

	Optional<Nanny> findByUserId(Long userId);

	// Admin "Nanny" detail — the nanny's own user row (phone/email/active), not the requesting admin's.
	@Query("SELECT n FROM Nanny n JOIN FETCH n.user WHERE n.id = :id")
	Optional<Nanny> findByIdWithUser(@Param("id") Long id);

	// Admin "Nannies" list — every filter optional except pagination. "Employment status" per the
	// admin spec has no backing column (no roster/attendance phase yet); filters instead on real
	// columns: verification rollup, account active flag, and whether mapped to a given zone.
	@Query(value = """
			SELECT n FROM Nanny n JOIN FETCH n.user u
			WHERE (:q IS NULL
			       OR LOWER(n.firstName) LIKE :q OR LOWER(n.lastName) LIKE :q OR LOWER(u.phone) LIKE :q OR LOWER(u.email) LIKE :q)
			  AND (:verificationStatus IS NULL OR n.overallVerificationStatus = :verificationStatus)
			  AND (:active IS NULL OR u.active = :active)
			  AND (:zoneAreaId IS NULL OR EXISTS (
			      SELECT 1 FROM CaregiverZoneMapping czm WHERE czm.caregiver.id = n.id AND czm.zoneArea.id = :zoneAreaId AND czm.active = true))
			ORDER BY n.createdAt DESC
			""",
			countQuery = """
			SELECT COUNT(n) FROM Nanny n JOIN n.user u
			WHERE (:q IS NULL
			       OR LOWER(n.firstName) LIKE :q OR LOWER(n.lastName) LIKE :q OR LOWER(u.phone) LIKE :q OR LOWER(u.email) LIKE :q)
			  AND (:verificationStatus IS NULL OR n.overallVerificationStatus = :verificationStatus)
			  AND (:active IS NULL OR u.active = :active)
			  AND (:zoneAreaId IS NULL OR EXISTS (
			      SELECT 1 FROM CaregiverZoneMapping czm WHERE czm.caregiver.id = n.id AND czm.zoneArea.id = :zoneAreaId AND czm.active = true))
			""")
	Page<Nanny> searchForAdmin(@Param("q") String q, @Param("verificationStatus") NannyVerificationStatus verificationStatus,
			@Param("active") Boolean active, @Param("zoneAreaId") Long zoneAreaId, Pageable pageable);

}
