package com.ektrepha.model;

/** {@code nanny_verification.status} — per-document status. Distinct from {@link NannyVerificationStatus}, the nanny-level rollup. */
public enum VerificationRecordStatus implements CodedEnum {

	PENDING(1),
	VERIFIED(2),
	REJECTED(3);

	private final int code;

	VerificationRecordStatus(int code) {
		this.code = code;
	}

	@Override
	public int code() {
		return code;
	}

}
