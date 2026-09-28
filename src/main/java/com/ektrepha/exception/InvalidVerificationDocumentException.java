package com.ektrepha.exception;

/** The uploaded file isn't a type this app accepts for a nanny verification document (see NannyVerificationServiceImpl#submitDocument). */
public class InvalidVerificationDocumentException extends RuntimeException {

	public InvalidVerificationDocumentException(String message) {
		super(message);
	}

}
