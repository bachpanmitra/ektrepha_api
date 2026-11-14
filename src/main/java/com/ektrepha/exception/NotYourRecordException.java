package com.ektrepha.exception;

import org.springframework.http.HttpStatus;

/** A write attempt on a parent/nanny record the caller doesn't own and isn't on duty for — CareManagerAccessService. */
public class NotYourRecordException extends CmApiException {

	public NotYourRecordException(String message) {
		super(HttpStatus.FORBIDDEN, "NOT_YOUR_RECORD", message);
	}

}
