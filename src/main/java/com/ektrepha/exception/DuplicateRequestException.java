package com.ektrepha.exception;

import org.springframework.http.HttpStatus;

/** A duplicate open request (CM/nanny change request already open), or an Idempotency-Key replay with a different request body. */
public class DuplicateRequestException extends CmApiException {

	public DuplicateRequestException(String message) {
		super(HttpStatus.CONFLICT, "DUPLICATE_REQUEST", message);
	}

}
