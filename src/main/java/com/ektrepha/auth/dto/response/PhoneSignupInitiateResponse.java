package com.ektrepha.auth.dto.response;

import java.util.UUID;

public record PhoneSignupInitiateResponse(
		String otpSentTo,
		long otpExpiresInSeconds,
		UUID signupSessionId) {
}
