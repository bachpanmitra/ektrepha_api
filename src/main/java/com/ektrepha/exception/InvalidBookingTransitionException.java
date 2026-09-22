package com.ektrepha.exception;

/** A booking lifecycle action (start/complete care) was requested from a status it doesn't apply to - e.g. completing a booking that hasn't started, or starting one that's already in progress. */
public class InvalidBookingTransitionException extends RuntimeException {

	public InvalidBookingTransitionException(String message) {
		super(message);
	}

}
