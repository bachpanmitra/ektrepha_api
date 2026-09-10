package com.ektrepha.verification;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import com.ektrepha.model.NannyVerificationStatus;
import com.ektrepha.model.VerificationDocType;
import com.ektrepha.model.VerificationRecordStatus;

/**
 * Pure rollup rule (PRD "Verification Status Filtering in Search" FR §4.3): VERIFIED only when
 * every required type is VERIFIED; PARTIAL if some but not all; PENDING otherwise; REJECTED if
 * any required type is REJECTED (this check wins over everything else). Non-required types
 * (FIRST_AID, REFERENCE) never affect the rollup.
 * <p>
 * Kept as a standalone, stateless class (not a private method on the service) so both the
 * per-nanny recompute path and the batch drift audit call the exact same logic.
 */
public final class VerificationRollupCalculator {

	private static final Set<VerificationDocType> REQUIRED_TYPES = EnumSet.of(
			VerificationDocType.ID_PROOF, VerificationDocType.BACKGROUND_CHECK, VerificationDocType.EDUCATION);

	private VerificationRollupCalculator() {
	}

	public static NannyVerificationStatus compute(Map<VerificationDocType, VerificationRecordStatus> latestStatusByType) {
		boolean anyRequiredRejected = REQUIRED_TYPES.stream()
				.anyMatch(type -> latestStatusByType.get(type) == VerificationRecordStatus.REJECTED);
		if (anyRequiredRejected) {
			return NannyVerificationStatus.REJECTED;
		}

		long verifiedCount = REQUIRED_TYPES.stream()
				.filter(type -> latestStatusByType.get(type) == VerificationRecordStatus.VERIFIED)
				.count();
		if (verifiedCount == REQUIRED_TYPES.size()) {
			return NannyVerificationStatus.VERIFIED;
		}
		if (verifiedCount > 0) {
			return NannyVerificationStatus.PARTIAL;
		}
		return NannyVerificationStatus.PENDING;
	}

}
