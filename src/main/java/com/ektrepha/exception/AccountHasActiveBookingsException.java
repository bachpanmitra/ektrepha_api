package com.ektrepha.exception;

public class AccountHasActiveBookingsException extends RuntimeException {

	public AccountHasActiveBookingsException(String message) {
		super(message);
	}

}
