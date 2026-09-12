package com.ektrepha.exception;

/** Thrown by the public search/pricing endpoints when the requested location or zone x service-type combo isn't serviceable at all (no zone match, or no active pricing row). */
public class NotServiceableException extends RuntimeException {

	public NotServiceableException(String message) {
		super(message);
	}

}
