package com.ektrepha.exception;

/**
 * A third-party identity/notification provider (Firebase Admin SDK, Brevo, ...) isn't usable right
 * now — missing/invalid server-side configuration, or the provider itself is unreachable. Distinct
 * from an invalid-input exception: the caller did nothing wrong, so this maps to 503, not 400.
 */
public class AuthProviderUnavailableException extends RuntimeException {

	public AuthProviderUnavailableException(String message) {
		super(message);
	}

	public AuthProviderUnavailableException(String message, Throwable cause) {
		super(message, cause);
	}

}
