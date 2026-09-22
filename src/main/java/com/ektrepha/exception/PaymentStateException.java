package com.ektrepha.exception;

/** A payment/booking is in the wrong state for the requested action - e.g. confirming a payment that's already SUCCESS, or paying for a booking that isn't AWAITING_PAYMENT. */
public class PaymentStateException extends RuntimeException {

	public PaymentStateException(String message) {
		super(message);
	}

}
