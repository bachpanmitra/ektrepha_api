package com.ektrepha.model;

/** {@code users.status} — added migration 006 (ACTIVE/DELETED), DEACTIVATED added migration 021. */
public enum UserStatus implements CodedEnum {

	ACTIVE(0),
	DELETED(1),
	DEACTIVATED(2);

	private final int code;

	UserStatus(int code) {
		this.code = code;
	}

	@Override
	public int code() {
		return code;
	}

}
