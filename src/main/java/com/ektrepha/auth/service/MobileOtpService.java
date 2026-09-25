package com.ektrepha.auth.service;

/**
 * Generates, delivers (via {@link SmsService}), and verifies the OTP challenges behind mobile
 * login/auto-signup (see {@code AuthController}'s {@code /mobile/otp/*} endpoints). See
 * MobileOtpServiceImpl.
 */
public interface MobileOtpService {

	record OtpChallenge(String challengeId, long expiresInSeconds, long resendAfterSeconds, String notice) {
	}

	/** Normalizes {@code mobileNumber}, enforces resend-cooldown/rate-limits, and creates+sends a new challenge, invalidating any still-live one for the same number. */
	OtpChallenge requestOtp(String mobileNumber, String clientIp);

	/** Verifies and atomically consumes the challenge (single-use). @return the challenge's normalized phone number. */
	String verifyAndConsume(String challengeId, String code);

}
