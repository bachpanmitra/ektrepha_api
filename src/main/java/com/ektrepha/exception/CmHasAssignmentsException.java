package com.ektrepha.exception;

import org.springframework.http.HttpStatus;

/** Deactivating a Care Manager while a parent or nanny is still assigned to them. */
public class CmHasAssignmentsException extends CmApiException {

	public CmHasAssignmentsException(String message) {
		super(HttpStatus.CONFLICT, "CM_HAS_ASSIGNMENTS", message);
	}

}
