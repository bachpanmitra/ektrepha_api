package com.ektrepha.auth.staff.dto.response;

/** {@code notice} is only set in dev/stage fixed-OTP test mode (e.g. "Test mode: SMS is not sent.") — null whenever a real SMS was actually sent. Mirrors {@code MobileOtpRequestResponse}, but {@code requestId} rather than {@code challengeId} — the nanny app's invented contract (see ektrepha-nanny-ui's docs/API_STATUS.md) never threads this value back into verify, unlike the parent app's challengeId. */
public record StaffOtpRequestResponse(
		String requestId,
		long expiresInSeconds,
		long resendAfterSeconds,
		String notice) {
}
