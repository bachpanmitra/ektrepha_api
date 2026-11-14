package com.ektrepha.exception;

import java.util.Map;

import org.springframework.http.HttpStatus;

import lombok.Getter;

/**
 * Base for every Care Manager feature exception. Unlike the rest of this codebase's exceptions
 * (grouped into generic status buckets by {@code GlobalExceptionHandler}), these carry their own
 * {@link #status}/{@link #code} so {@code CareManagerExceptionHandler} can map them with one
 * generic handler and produce the contract's nested {@code {error:{code,message,request_id,fields}}}
 * shape — see docs/api/openapi.yaml#/components/schemas/Error.
 */
@Getter
public abstract class CmApiException extends RuntimeException {

	private final HttpStatus status;
	private final String code;
	private final transient Map<String, String> fields;

	protected CmApiException(HttpStatus status, String code, String message) {
		this(status, code, message, null);
	}

	protected CmApiException(HttpStatus status, String code, String message, Map<String, String> fields) {
		super(message);
		this.status = status;
		this.code = code;
		this.fields = fields;
	}

}
