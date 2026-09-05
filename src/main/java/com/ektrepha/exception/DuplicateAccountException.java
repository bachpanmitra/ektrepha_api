package com.ektrepha.exception;

public class DuplicateAccountException extends RuntimeException {

	public DuplicateAccountException(String message) {
		super(message);
	}

	public static DuplicateAccountException email(String email) {
		return new DuplicateAccountException("An account with email " + email + " already exists");
	}

	public static DuplicateAccountException phone(String phone) {
		return new DuplicateAccountException("An account with phone number " + phone + " already exists");
	}

}
