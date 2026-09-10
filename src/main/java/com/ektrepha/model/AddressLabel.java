package com.ektrepha.model;

/** {@code parent_address.label}. */
public enum AddressLabel implements CodedEnum {

	HOME(1),
	WORK(2),
	OTHER(3);

	private final int code;

	AddressLabel(int code) {
		this.code = code;
	}

	@Override
	public int code() {
		return code;
	}

}
