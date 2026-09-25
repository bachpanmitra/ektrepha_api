package com.ektrepha.exception;

/** The free-tier usage budget for one Ola Maps API (reverse-geocode/autocomplete/place-details) is exhausted for the current window — the client should fall back to manual address entry rather than retry. */
public class LocationQuotaExceededException extends RuntimeException {

	public LocationQuotaExceededException(String message) {
		super(message);
	}

}
