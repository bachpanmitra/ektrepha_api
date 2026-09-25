package com.ektrepha.auth.dto.response;

import java.time.Instant;

public record MobileOtpVerifyResponse(
		String accessToken,
		String refreshToken,
		Instant sessionExpiresAt,
		boolean isNewUser,
		boolean profileComplete,
		MobileUser user) {

	public record MobileUser(Long id, String mobileNumber, boolean mobileVerified) {
	}

}
