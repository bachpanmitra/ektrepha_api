package com.ektrepha.exception;

/** No in-house caregiver capacity is free for the requested zone/service/time window (hourly-care flow's own availability check, not a specific nanny's DB-level conflict). */
public class CareUnavailableException extends RuntimeException {

	public CareUnavailableException(String message) {
		super(message);
	}

}
