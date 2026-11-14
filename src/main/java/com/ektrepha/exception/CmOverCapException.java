package com.ektrepha.exception;

import org.springframework.http.HttpStatus;

/** A connect/reassign would push a Care Manager's active-parent count over max_parents. */
public class CmOverCapException extends CmApiException {

	public CmOverCapException(String message) {
		super(HttpStatus.CONFLICT, "CM_OVER_CAP", message);
	}

}
