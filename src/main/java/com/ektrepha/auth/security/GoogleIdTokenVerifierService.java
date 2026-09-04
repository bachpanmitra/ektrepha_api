package com.ektrepha.auth.security;

/** Verifies Google ID tokens against app.google.client-id. See {@link GoogleIdTokenVerifierServiceImpl}. */
public interface GoogleIdTokenVerifierService {

	/** Extracted, verified identity claims from a Google ID token. */
	record GoogleIdentity(String googleId, String email, String name) {
	}

	GoogleIdentity verify(String idTokenString);

}
