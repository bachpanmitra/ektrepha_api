package com.ektrepha.verification.service;

import java.util.List;

import org.springframework.web.multipart.MultipartFile;

import com.ektrepha.model.NannyVerification;
import com.ektrepha.model.NannyVerificationStatus;
import com.ektrepha.model.VerificationDocType;
import com.ektrepha.model.VerificationRecordStatus;

public interface NannyVerificationService {

	/** One nanny whose stored rollup doesn't match what recompute would produce from its current nanny_verification rows. */
	record DriftRecord(Long nannyId, NannyVerificationStatus storedStatus, NannyVerificationStatus expectedStatus) {
	}

	/**
	 * Stores {@code file} in S3 and inserts a new PENDING {@code nanny_verification} row of the given
	 * {@code type} for the nanny profile owned by {@code userId}, then recomputes that nanny's rollup
	 * (a fresh PENDING row can drop a previously-VERIFIED rollup back to PARTIAL/PENDING).
	 */
	NannyVerification submitDocument(Long userId, VerificationDocType type, MultipartFile file);

	/**
	 * Updates one verification record's status/reviewer/rejection-reason and recomputes the owning
	 * nanny's rollup in the same transaction. This is the sole trigger point for
	 * overall_verification_status changes — no other code path may change it.
	 */
	NannyVerification updateRecordStatus(Long verificationRecordId, VerificationRecordStatus newStatus, Long reviewedByUserId, String rejectionReason);

	/** Recomputes and persists one nanny's rollup from their current nanny_verification rows. */
	void recompute(Long nannyId);

	/** Scans every nanny and returns those whose stored rollup doesn't match what recompute would produce — the drift audit. */
	List<DriftRecord> findDrift();

}
