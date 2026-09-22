package com.ektrepha.model;

/** {@code payment_transaction.method} - how the parent chose to pay on the hourly-care Payment screen. */
public enum PaymentMethod implements CodedEnum {

	UPI(1),
	CARD(2);

	private final int code;

	PaymentMethod(int code) {
		this.code = code;
	}

	@Override
	public int code() {
		return code;
	}

}
