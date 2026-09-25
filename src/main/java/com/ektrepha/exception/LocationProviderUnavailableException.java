package com.ektrepha.exception;

/** Ola Maps timed out, errored, or returned an unparseable response — never surfaced to the client as a raw provider error; the client should fall back to manual address entry. */
public class LocationProviderUnavailableException extends RuntimeException {

	public LocationProviderUnavailableException(String message) {
		super(message);
	}

}
