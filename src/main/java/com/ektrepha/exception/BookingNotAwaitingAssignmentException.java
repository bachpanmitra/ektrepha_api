package com.ektrepha.exception;

/** Ops tried to assign a caregiver to a booking that isn't ASSIGNING_CAREGIVER (e.g. still awaiting payment, already assigned, or cancelled). */
public class BookingNotAwaitingAssignmentException extends RuntimeException {

	public BookingNotAwaitingAssignmentException(String message) {
		super(message);
	}

}
