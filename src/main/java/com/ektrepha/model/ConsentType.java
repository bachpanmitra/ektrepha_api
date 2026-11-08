package com.ektrepha.model;

/** {@code nanny_consent.type} — what the nanny has agreed to before the app does it (API Map row 39). */
public enum ConsentType implements CodedEnum {

	DOCUMENT_UPLOAD(1),
	LOCATION(2);

	private final int code;

	ConsentType(int code) {
		this.code = code;
	}

	@Override
	public int code() {
		return code;
	}

}
