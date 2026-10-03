package com.ektrepha.verification;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import com.ektrepha.model.InterviewOutcome;
import com.ektrepha.model.NannyVerificationStatus;
import com.ektrepha.model.VerificationDocType;
import com.ektrepha.model.VerificationRecordStatus;

/**
 * Pure rollup rule for the full caregiver-verification state machine (child-safety program,
 * migration 38) - APPROVED only when every one of the following holds, REJECTED if any required
 * document is REJECTED or the interview outcome is FAILED (this check wins over everything
 * else), UNDER_REVIEW if some progress has been made but not all gates are met, PENDING if
 * nothing has been submitted at all:
 * <ul>
 * <li>18+ (by DOB)</li>
 * <li>ID_PROOF, BACKGROUND_CHECK (PCC), ADDRESS_PROOF and LIVENESS_SELFIE all VERIFIED</li>
 * <li>the VERIFIED PCC's expiry date (if any) has not passed</li>
 * <li>at least {@link #MIN_VERIFIED_REFERENCES} references VERIFIED</li>
 * <li>the latest interview outcome is PASSED</li>
 * <li>at least one training/quiz attempt passed</li>
 * <li>the code of conduct has been accepted</li>
 * </ul>
 * EDUCATION and FIRST_AID are never required - they never affect the rollup, same as before this
 * overhaul.
 * <p>
 * Kept as a standalone, stateless class (not a private method on the service) so both the
 * per-nanny recompute path and the batch drift audit call the exact same logic.
 */
public final class VerificationRollupCalculator {

	public static final int MIN_VERIFIED_REFERENCES = 2;

	private static final Set<VerificationDocType> REQUIRED_DOC_TYPES = EnumSet.of(
			VerificationDocType.ID_PROOF, VerificationDocType.BACKGROUND_CHECK,
			VerificationDocType.ADDRESS_PROOF, VerificationDocType.LIVENESS_SELFIE);

	private VerificationRollupCalculator() {
	}

	/**
	 * @param atLeast18               from {@code Nanny#isAtLeast18}
	 * @param latestStatusByType      latest {@code VerificationRecordStatus} per submitted doc type (may omit types never submitted)
	 * @param verifiedPccExpiryDate   the latest VERIFIED BACKGROUND_CHECK record's expiry date, or null if not VERIFIED or no expiry on file
	 * @param referenceCount          total references submitted (any status) - used only to distinguish PENDING from UNDER_REVIEW
	 * @param verifiedReferenceCount  references with status VERIFIED
	 * @param latestInterviewOutcome  the most recent interview's outcome, or null if none scheduled yet
	 * @param trainingPassed          true if any training attempt passed
	 * @param codeOfConductAccepted   true if the current code-of-conduct version has been accepted
	 */
	public record RollupInputs(
			boolean atLeast18,
			Map<VerificationDocType, VerificationRecordStatus> latestStatusByType,
			LocalDate verifiedPccExpiryDate,
			int referenceCount,
			int verifiedReferenceCount,
			InterviewOutcome latestInterviewOutcome,
			boolean trainingPassed,
			boolean codeOfConductAccepted) {
	}

	public static NannyVerificationStatus compute(RollupInputs in) {
		boolean anyRequiredDocRejected = REQUIRED_DOC_TYPES.stream()
				.anyMatch(type -> in.latestStatusByType().get(type) == VerificationRecordStatus.REJECTED);
		boolean interviewFailed = in.latestInterviewOutcome() == InterviewOutcome.FAILED;
		if (anyRequiredDocRejected || interviewFailed) {
			return NannyVerificationStatus.REJECTED;
		}

		boolean allRequiredDocsVerified = REQUIRED_DOC_TYPES.stream()
				.allMatch(type -> in.latestStatusByType().get(type) == VerificationRecordStatus.VERIFIED);
		boolean pccNotExpired = in.verifiedPccExpiryDate() == null || !in.verifiedPccExpiryDate().isBefore(LocalDate.now());
		boolean referencesMet = in.verifiedReferenceCount() >= MIN_VERIFIED_REFERENCES;
		boolean interviewPassed = in.latestInterviewOutcome() == InterviewOutcome.PASSED;

		boolean allGatesPassed = in.atLeast18() && allRequiredDocsVerified && pccNotExpired && referencesMet
				&& interviewPassed && in.trainingPassed() && in.codeOfConductAccepted();
		if (allGatesPassed) {
			return NannyVerificationStatus.APPROVED;
		}

		boolean anyProgress = in.atLeast18() || !in.latestStatusByType().isEmpty() || in.referenceCount() > 0
				|| in.latestInterviewOutcome() != null || in.trainingPassed() || in.codeOfConductAccepted();
		return anyProgress ? NannyVerificationStatus.UNDER_REVIEW : NannyVerificationStatus.PENDING;
	}

}
