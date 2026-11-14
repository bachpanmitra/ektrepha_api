package com.ektrepha.exception;

import org.springframework.http.HttpStatus;

/** A CM-only entity (CareManager, CmTask, Complaint, CmDutyShift, ...) looked up by public id that doesn't exist. */
public class CmResourceNotFoundException extends CmApiException {

	public CmResourceNotFoundException(String message) {
		super(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
	}

}
