package com.ektrepha.verification.service;

import java.time.LocalDate;
import java.util.List;

import org.springframework.web.multipart.MultipartFile;

import com.ektrepha.model.NannyVerification;
import com.ektrepha.model.NannyVerificationStatus;
import com.ektrepha.model.VerificationDocType;
import com.ektrepha.model.VerificationRecordStatus;

public interface NannyVerificationService {

	/** One nanny whose stored rollup doesn't match what recompute would produce from its current signals. */
	record DriftRecord(Long nannyId, NannyVerificationStatus storedStatus, NannyVerificationStatus expectedStatus) {
	}

	/**
	 * Stores {@code file} in S3 and inserts a new PENDING {@code nanny_verification} row of the
	 * given {@code type} for the nanny profile owned by {@code userId}, then recomputes that
	 * nanny's rollup. For {@link VerificationDocType#ID_PROOF} and
	 * {@link VerificationDocType#LIVENESS_SELFIE}, also runs the document through
	 * {@code KycVerificationProvider} and checks the resulting hash against
	 * {@code BanEvasionCheckService} before the row is persisted - a match throws and nothing is
	 * saved. {@code expiryDate} is only meaningful for {@link VerificationDocType#BACKGROUND_CHECK}
	 * (the PCC); pass null for every other type. {@code deviceId} (the mobile app's own device
	 * identifier) is optional - when present, it's also checked against
	 * {@code BanEvasionCheckService} and recorded on the nanny for future bans to seed from.
	 */
	NannyVerification submitDocument(Long userId, VerificationDocType type, MultipartFile file, LocalDate expiryDate, String deviceId);

	/**
	 * Same as {@link #submitDocument}, but for an ADMIN uploading on behalf of a nanny identified
	 * directly by {@code nannyId} (e.g. a physical document collected in person), rather than
	 * resolving the nanny from the caller's own user id. There is no device id for an admin-collected
	 * document.
	 */
	NannyVerification submitDocumentForNanny(Long nannyId, VerificationDocType type, MultipartFile file, LocalDate expiryDate);

	/**
	 * Updates one verification record's status/reviewer/rejection-reason and recomputes the owning
	 * nanny's rollup in the same transaction. This is the sole trigger point for an *automatic*
	 * overall_verification_status change from document review — {@link #changeStatus} is the sole
	 * trigger point for a manual one (approve/reject/suspend/ban/reinstate).
	 */
	NannyVerification updateRecordStatus(Long verificationRecordId, VerificationRecordStatus newStatus, Long reviewedByUserId, String rejectionReason);

	/**
	 * Recomputes and persists one nanny's rollup from their current full set of onboarding signals
	 * (documents, references, interview, training, code of conduct, age). No-op on the stored
	 * status if it's currently SUSPENDED or BANNED - see {@code Nanny#applyRecomputedVerificationStatus}.
	 */
	void recompute(Long nannyId);

	/**
	 * Explicit admin (or, for SUSPENDED via expiry/re-verification, system) transition - always
	 * takes effect regardless of the nanny's current status, records a {@code nanny_status_history}
	 * row, and (only when {@code newStatus} is BANNED) seeds {@code banned_identity} with this
	 * nanny's known hashes/device id so a future signup attempt under a different account is
	 * caught. {@code changedByUserId} is null for a system transition.
	 */
	void changeStatus(Long nannyId, NannyVerificationStatus newStatus, String reason, Long changedByUserId);

	/** Scans every nanny and returns those whose stored rollup doesn't match what recompute would produce — the drift audit. Never flags a SUSPENDED/BANNED nanny as drifted, since recompute deliberately never overwrites those. */
	List<DriftRecord> findDrift();

}
