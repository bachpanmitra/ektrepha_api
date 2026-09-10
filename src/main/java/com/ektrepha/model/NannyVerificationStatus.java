package com.ektrepha.model;

/** {@code nanny.overall_verification_status} — derived rollup, recomputed from {@link VerificationRecordStatus} rows. */
public enum NannyVerificationStatus implements CodedEnum {

	PENDING(1),
	PARTIAL(2),
	VERIFIED(3),
	REJECTED(4);

	private final int code;

	NannyVerificationStatus(int code) {
		this.code = code;
	}

	@Override
	public int code() {
		return code;
	}

}
