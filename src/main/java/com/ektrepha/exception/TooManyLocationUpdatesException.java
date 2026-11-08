package com.ektrepha.exception;

/** The nanny app is sending location pings faster than the 1-per-10s budget (API Map row 51 step 1). */
public class TooManyLocationUpdatesException extends RuntimeException {

	public TooManyLocationUpdatesException(String message) {
		super(message);
	}

}
