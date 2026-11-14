package com.ektrepha.exception;

import org.springframework.http.HttpStatus;

/** PUT /cm/parents/{id}/notes with a stale If-Match version. */
public class VersionMismatchException extends CmApiException {

	public VersionMismatchException(String message) {
		super(HttpStatus.PRECONDITION_FAILED, "VERSION_MISMATCH", message);
	}

}
