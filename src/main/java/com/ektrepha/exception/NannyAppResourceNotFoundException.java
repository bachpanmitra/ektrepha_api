package com.ektrepha.exception;

/** A nanny-app resource (payslip, notification, a shift on a given date...) that doesn't exist or isn't the caller's - 404. */
public class NannyAppResourceNotFoundException extends RuntimeException {

	public NannyAppResourceNotFoundException(String message) {
		super(message);
	}

}
