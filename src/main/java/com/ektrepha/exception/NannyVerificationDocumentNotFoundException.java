package com.ektrepha.exception;

/** No {@code nanny_verification} row with the given id, or it doesn't belong to the caller (same 404 either way — never reveal which, see test NAO-72). */
public class NannyVerificationDocumentNotFoundException extends RuntimeException {

	public NannyVerificationDocumentNotFoundException(String message) {
		super(message);
	}

}
