package com.ektrepha.model;

/**
 * {@code nanny_verification.type} - which kind of document/claim this row verifies.
 * <p>
 * {@code BACKGROUND_CHECK} is the Police Clearance Certificate (PCC) - the only type that ever
 * carries {@code nanny_verification.expiry_date}. {@code REFERENCE} is legacy (migration 38
 * replaced it with the structured, independently-verified {@code nanny_reference} table, which
 * supports the spec's "minimum 2 references" requirement; a single document slot can't).
 */
public enum VerificationDocType implements CodedEnum {

	ID_PROOF(1),
	BACKGROUND_CHECK(2),
	EDUCATION(3),
	FIRST_AID(4),
	REFERENCE(5),
	ADDRESS_PROOF(6),
	LIVENESS_SELFIE(7);

	private final int code;

	VerificationDocType(int code) {
		this.code = code;
	}

	@Override
	public int code() {
		return code;
	}

}
