package com.ektrepha.model;

/**
 * {@code nanny.overall_verification_status} - derived rollup, recomputed from the full set of
 * onboarding signals (see {@code com.ektrepha.verification.VerificationRollupCalculator}):
 * document verification, references, interview outcome, training/quiz, code-of-conduct
 * acceptance and age.
 * <p>
 * UNDER_REVIEW and APPROVED were named PARTIAL/VERIFIED before the caregiver-verification
 * overhaul (migration 38) - same codes (2/3), renamed to match the spec's state machine.
 * SUSPENDED and BANNED are new: unlike the other four, they are never set by
 * {@code VerificationRollupCalculator.compute} - only by an explicit admin action or the
 * PCC-expiry/re-verification scheduled job, and the recompute path must never silently overwrite
 * them (see {@code Nanny#applyRecomputedVerificationStatus}).
 */
public enum NannyVerificationStatus implements CodedEnum {

	PENDING(1),
	UNDER_REVIEW(2),
	APPROVED(3),
	REJECTED(4),
	SUSPENDED(5),
	BANNED(6);

	private final int code;

	NannyVerificationStatus(int code) {
		this.code = code;
	}

	@Override
	public int code() {
		return code;
	}

}
