package com.ektrepha.auth.dto.response;

/** {@code notice} is only set in dev/stage fixed-OTP test mode (e.g. "Test mode: SMS is not sent.") — null whenever a real SMS was actually sent. */
public record MobileOtpRequestResponse(
		String challengeId,
		long expiresInSeconds,
		long resendAfterSeconds,
		String notice) {
}
