package com.ektrepha.exception;

import org.springframework.http.HttpStatus;

/** Care Manager feature's own per-role rate limit (parent 60/min, admin panel 600/min) — distinct from the existing global per-IP RateLimitFilter. */
public class CmRateLimitedException extends CmApiException {

	public CmRateLimitedException(String message) {
		super(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", message);
	}

}
