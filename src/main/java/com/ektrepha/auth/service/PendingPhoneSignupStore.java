package com.ektrepha.auth.service;

import java.time.Instant;
import java.util.UUID;

import com.ektrepha.model.UserType;

/**
 * Bridges phone-number, password-hash and role between
 * /signup/phone/initiate and /signup/phone/verify. See
 * {@link PendingPhoneSignupStoreImpl} for why this is in-memory rather than
 * a table.
 */
public interface PendingPhoneSignupStore {

	record PendingPhoneSignup(String phoneNumber, String passwordHash, UserType role, Instant expiresAt) {
	}

	UUID put(String phoneNumber, String passwordHash, UserType role, Instant expiresAt);

	PendingPhoneSignup get(UUID sessionId);

	void remove(UUID sessionId);

}
