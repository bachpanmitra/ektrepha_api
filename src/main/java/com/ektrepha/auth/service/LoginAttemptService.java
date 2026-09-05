package com.ektrepha.auth.service;

/** In-memory login-attempt lockout keyed by identifier (email/phone). See {@link LoginAttemptServiceImpl}. */
public interface LoginAttemptService {

	void assertNotLocked(String key);

	void recordFailure(String key);

	void recordSuccess(String key);

}
