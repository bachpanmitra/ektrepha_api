package com.ektrepha.model;

/** {@code nanny_verification.type} — which kind of document/claim this row verifies. */
public enum VerificationDocType implements CodedEnum {

	ID_PROOF(1),
	BACKGROUND_CHECK(2),
	EDUCATION(3),
	FIRST_AID(4),
	REFERENCE(5);

	private final int code;

	VerificationDocType(int code) {
		this.code = code;
	}

	@Override
	public int code() {
		return code;
	}

}
