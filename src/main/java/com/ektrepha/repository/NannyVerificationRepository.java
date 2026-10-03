package com.ektrepha.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.ektrepha.model.NannyVerification;
import com.ektrepha.model.VerificationRecordStatus;

public interface NannyVerificationRepository extends JpaRepository<NannyVerification, Long> {

	List<NannyVerification> findByNannyId(Long nannyId);

	long countByStatus(VerificationRecordStatus status);

	// For VerificationExpiryAuditJob: every nanny whose MOST RECENT BACKGROUND_CHECK (PCC) row is
	// VERIFIED but its expiry_date has passed. "Most recent" matters because an older, expired PCC
	// must not re-trigger a suspension once a fresh one has been submitted and verified - only the
	// latest row per (nanny, type) counts, same rule VerificationRollupCalculator applies.
	@Query(value = """
			SELECT nv.nanny_id FROM nanny_verification nv
			WHERE nv.type = 2 AND nv.status = 2 AND nv.expiry_date < CURRENT_DATE
			  AND nv.created_at = (
			      SELECT MAX(nv2.created_at) FROM nanny_verification nv2
			      WHERE nv2.nanny_id = nv.nanny_id AND nv2.type = 2
			  )
			""", nativeQuery = true)
	List<Long> findNannyIdsWithExpiredVerifiedPcc();

}
