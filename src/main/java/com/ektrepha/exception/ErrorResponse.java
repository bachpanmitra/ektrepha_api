package com.ektrepha.exception;

import java.time.Instant;

/** {@code code} is null for every pre-existing exception — only new, machine-readable-error-code cases (e.g. QUOTE_EXPIRED, TRIAL_REVOKED) set it. */
public record ErrorResponse(Instant timestamp, int status, String error, String message, String path, String code) {
}
