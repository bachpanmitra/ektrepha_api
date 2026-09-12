package com.ektrepha.auth.security;

/** Verifies Firebase phone-auth ID tokens. See {@link FirebaseTokenVerifierServiceImpl}. */
public interface FirebaseTokenVerifierService {

	/** Extracted, verified identity claims from a Firebase phone-auth ID token. */
	record FirebaseIdentity(String uid, String phoneNumber) {
	}

	FirebaseIdentity verify(String idToken);

}
