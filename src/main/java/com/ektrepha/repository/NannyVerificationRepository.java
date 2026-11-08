package com.ektrepha.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.ektrepha.model.NannyVerification;
import com.ektrepha.model.VerificationRecordStatus;

public interface NannyVerificationRepository extends JpaRepository<NannyVerification, Long> {

	List<NannyVerification> findByNannyId(Long nannyId);

	long countByStatus(VerificationRecordStatus status);

	// Admin review queue (API Map row 20) — join-fetches nanny+user so the controller can show
	// who each row belongs to without an N+1 per row. Oldest-first: a FIFO queue, same as any
	// other ops review backlog.
	@Query("""
			SELECT nv FROM NannyVerification nv
			JOIN FETCH nv.nanny n
			JOIN FETCH n.user
			WHERE nv.status = :status
			ORDER BY nv.createdAt ASC
			""")
	List<NannyVerification> findByStatusForReviewQueue(VerificationRecordStatus status);

	// For VerificationExpiryAuditJob's expiry-reminder sweep (API Map row 26): the MOST RECENT
	// VERIFIED BACKGROUND_CHECK (PCC) per nanny, due within 30 days, not yet reminded. Same
	// latest-row-per-(nanny,type) shape as findNannyIdsWithExpiredVerifiedPcc below. Returns flat
	// columns (verification id, nanny's user id, expiry date), not entities - the job processes
	// each row in its own short-lived transaction (see markExpiryReminderSent), so there's no open
	// session later to lazy-load nv.nanny.user through.
	@Query(value = """
			SELECT nv.id, n.user_id, nv.expiry_date FROM nanny_verification nv
			JOIN nanny n ON n.id = nv.nanny_id
			WHERE nv.type = 2 AND nv.status = 2 AND nv.expiry_reminder_sent_at IS NULL
			  AND nv.expiry_date BETWEEN CURRENT_DATE AND CURRENT_DATE + 30
			  AND nv.created_at = (
			      SELECT MAX(nv2.created_at) FROM nanny_verification nv2
			      WHERE nv2.nanny_id = nv.nanny_id AND nv2.type = 2
			  )
			""", nativeQuery = true)
	List<Object[]> findDueForExpiryReminderRows();

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
