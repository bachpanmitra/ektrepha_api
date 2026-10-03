package com.ektrepha.exception;

/** Ops tried to reassign a booking that isn't eligible - not CONFIRMED yet, already checked in, or has no caregiver to replace (use assign instead). */
public class BookingNotReassignableException extends RuntimeException {

	public BookingNotReassignableException(String message) {
		super(message);
	}

}
