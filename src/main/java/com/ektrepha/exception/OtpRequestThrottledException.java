package com.ektrepha.exception;

public class OtpRequestThrottledException extends RuntimeException {

	public OtpRequestThrottledException(String message) {
		super(message);
	}

}
