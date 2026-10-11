package com.ektrepha.exception;

/** A nanny-app action refused by a business rule (outside the check-in geofence/time window, not enough leave left, duplicate review...) - 409. */
public class NannyAppRuleViolationException extends RuntimeException {

	public NannyAppRuleViolationException(String message) {
		super(message);
	}

}
