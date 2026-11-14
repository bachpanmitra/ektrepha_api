package com.ektrepha.exception;

import java.util.Map;

import org.springframework.http.HttpStatus;

/** Cross-field/domain validation failures the bean-validation layer can't express (e.g. "resolution required when status is CLOSED"). 422 VALIDATION_FAILED, not 400 — this contract's explicit mapping. */
public class CmValidationException extends CmApiException {

	public CmValidationException(String message) {
		super(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_FAILED", message);
	}

	public CmValidationException(String message, Map<String, String> fields) {
		super(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_FAILED", message, fields);
	}

}
