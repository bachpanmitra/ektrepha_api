package com.ektrepha.exception;

/** The DB's {@code no_overlapping_bookings} EXCLUDE constraint rejected the insert — the nanny already has a conflicting booking in this window. */
public class NannyUnavailableException extends RuntimeException {

	public NannyUnavailableException(String message) {
		super(message);
	}

}
