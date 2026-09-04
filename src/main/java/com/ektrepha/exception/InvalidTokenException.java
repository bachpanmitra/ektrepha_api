package com.ektrepha.exception;

public class InvalidTokenException extends RuntimeException {

	public InvalidTokenException() {
		super("Token is invalid, expired, or already used");
	}

}
