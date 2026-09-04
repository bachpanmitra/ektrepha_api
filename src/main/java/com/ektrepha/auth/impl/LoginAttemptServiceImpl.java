package com.ektrepha.auth.impl;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Component;

import com.ektrepha.exception.AccountLockedException;
import com.ektrepha.config.properties.AppProperties;
import com.ektrepha.auth.service.LoginAttemptService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * In-memory login-attempt lockout, keyed by identifier (email/phone), per
 * app.login-lockout config. No dedicated table for this in the given
 * schema, and Redis isn't wired into the app yet — in-memory is fine for a
 * single instance; would need Redis to share lockout state across instances.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoginAttemptServiceImpl implements LoginAttemptService {

	private record AttemptState(AtomicInteger failures, Instant lockedUntil) {
		AttemptState() {
			this(new AtomicInteger(0), null);
		}
	}

	private final Map<String, AttemptState> attempts = new ConcurrentHashMap<>();
	private final AppProperties appProperties;

	@Override
	public void assertNotLocked(String key) {
		AttemptState state = attempts.get(key);
		if (state != null && state.lockedUntil() != null && state.lockedUntil().isAfter(Instant.now())) {
			log.warn("Login attempt blocked: {} is locked out until {}", key, state.lockedUntil());
			throw new AccountLockedException("Too many failed attempts. Try again later.");
		}
	}

	@Override
	public void recordFailure(String key) {
		AttemptState state = attempts.computeIfAbsent(key, k -> new AttemptState());
		int failures = state.failures().incrementAndGet();
		if (failures >= appProperties.loginLockout().maxFailures()) {
			Instant lockedUntil = Instant.now().plus(Duration.ofMinutes(appProperties.loginLockout().lockoutMinutes()));
			attempts.put(key, new AttemptState(state.failures(), lockedUntil));
			log.warn("Account locked out: {} reached {} failed attempts, locked until {}", key, failures, lockedUntil);
		} else {
			log.debug("Login failure recorded for {}: {}/{} before lockout", key, failures, appProperties.loginLockout().maxFailures());
		}
	}

	@Override
	public void recordSuccess(String key) {
		attempts.remove(key);
	}

}
