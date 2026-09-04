package com.ektrepha.auth.impl;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.ektrepha.model.UserType;
import com.ektrepha.auth.service.PendingPhoneSignupStore;

/**
 * Holds phone-number, password-hash and role between /signup/phone/initiate
 * and /signup/phone/verify. There's nowhere in the 3-table schema to persist
 * this pending state (users can't be created until the OTP is verified, and
 * the otps table has no password column), so it lives in memory for the OTP
 * TTL window only. Fine for a single-instance deployment; would need to move
 * to Redis if this ever runs behind multiple app instances.
 */
@Component
public class PendingPhoneSignupStoreImpl implements PendingPhoneSignupStore {

	private final Map<UUID, PendingPhoneSignup> pending = new ConcurrentHashMap<>();

	@Override
	public UUID put(String phoneNumber, String passwordHash, UserType role, Instant expiresAt) {
		UUID sessionId = UUID.randomUUID();
		pending.put(sessionId, new PendingPhoneSignup(phoneNumber, passwordHash, role, expiresAt));
		return sessionId;
	}

	@Override
	public PendingPhoneSignup get(UUID sessionId) {
		PendingPhoneSignup entry = pending.get(sessionId);
		if (entry == null) {
			return null;
		}
		if (entry.expiresAt().isBefore(Instant.now())) {
			pending.remove(sessionId);
			return null;
		}
		return entry;
	}

	@Override
	public void remove(UUID sessionId) {
		pending.remove(sessionId);
	}

}
