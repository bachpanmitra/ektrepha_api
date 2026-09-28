package com.ektrepha.auth.security;

/** Verifies Google ID tokens against app.google.client-id. See {@link GoogleIdTokenVerifierServiceImpl}. */
public interface GoogleIdTokenVerifierService {

	/**
	 * Extracted, verified identity claims from a Google ID token. {@code emailVerified} is Google's
	 * {@code email_verified} claim — only a verified email may be used to link an existing account.
	 */
	record GoogleIdentity(String googleId, String email, String name, boolean emailVerified) {
	}

	GoogleIdentity verify(String idTokenString);

}
