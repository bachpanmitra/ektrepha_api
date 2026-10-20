package com.ektrepha.exception;

/** A {@code hourly_care_quote} was used past its {@code expiresAt} (or already used once) — the app should re-quote. */
public class QuoteExpiredException extends RuntimeException {

	public QuoteExpiredException(String message) {
		super(message);
	}

	public String code() {
		return "QUOTE_EXPIRED";
	}

}
