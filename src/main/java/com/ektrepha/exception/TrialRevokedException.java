package com.ektrepha.exception;

/**
 * Thrown when a duplicate care-address is detected for a FIRST5_FEE_WAIVER trial before any
 * payment order exists for the booking being reserved — the one abuse signal this flow can catch
 * pre-charge (see {@code com.ektrepha.trial.impl.TrialServiceImpl}). The app should re-quote
 * (which will no longer carry a trial discount) and show the full price. Never exposes which check
 * failed.
 */
public class TrialRevokedException extends RuntimeException {

	public TrialRevokedException(String message) {
		super(message);
	}

	public String code() {
		return "TRIAL_REVOKED";
	}

}
