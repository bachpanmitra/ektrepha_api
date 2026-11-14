package com.ektrepha.exception;

import org.springframework.http.HttpStatus;

/** A parent/nanny is already assigned to the Care Manager named in the request (connect/reassign), or a CM phone/email is already in use (create). */
public class AlreadyAssignedException extends CmApiException {

	public AlreadyAssignedException(String message) {
		super(HttpStatus.CONFLICT, "ALREADY_ASSIGNED", message);
	}

}
