package com.ektrepha.config.constants;

/**
 * Shared string constants for the JWT/auth/tracing infrastructure —
 * centralized so the same literal (a claim name, a header, a lock-key
 * prefix) can't quietly drift between the place that writes it and the
 * place that reads it.
 */
public final class SecurityConstants {

	private SecurityConstants() {
	}

	// --- JWT ---
	public static final String BEARER_PREFIX = "Bearer ";
	public static final String ROLE_AUTHORITY_PREFIX = "ROLE_";
	public static final String CLAIM_USER_TYPE = "userType";
	public static final String CLAIM_EMAIL = "email";
	public static final String CLAIM_PHONE = "phone";

	// --- Request tracing ---
	public static final String TRACE_ID_MDC_KEY = "traceId";
	public static final String TRACE_ID_HEADER = "X-Trace-Id";

	// --- Login-lockout keys (see LoginAttemptService) ---
	public static final String LOGIN_LOCK_KEY_PHONE_PREFIX = "phone:";
	public static final String LOGIN_LOCK_KEY_EMAIL_PREFIX = "email:";

}
