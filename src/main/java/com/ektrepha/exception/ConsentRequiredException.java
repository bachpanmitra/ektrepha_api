package com.ektrepha.exception;

/** The nanny hasn't granted a required {@code ConsentType} yet (API Map row 51 step 5 — location consent gates live-location pings). 428 Precondition Required: the client should prompt for consent, then retry. */
public class ConsentRequiredException extends RuntimeException {

	public ConsentRequiredException(String message) {
		super(message);
	}

}
