package com.ektrepha.model;

/**
 * {@code payment_transaction.status}. No payment gateway is integrated yet - INITIATED is set when
 * the parent picks a method on the Payment screen, and SUCCESS/FAILED are set by a confirm/fail
 * call that stands in for a real gateway webhook (see com.ektrepha.hourlycare).
 */
public enum PaymentStatus implements CodedEnum {

	INITIATED(1),
	SUCCESS(2),
	FAILED(3);

	private final int code;

	PaymentStatus(int code) {
		this.code = code;
	}

	@Override
	public int code() {
		return code;
	}

}
