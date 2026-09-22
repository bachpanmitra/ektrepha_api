package com.ektrepha.exception;

/** GET /bookings/{id}/contact on a PENDING booking — no confirmed nanny to reveal a number for yet. */
public class BookingContactNotAvailableException extends RuntimeException {

	public BookingContactNotAvailableException(String message) {
		super(message);
	}

}
