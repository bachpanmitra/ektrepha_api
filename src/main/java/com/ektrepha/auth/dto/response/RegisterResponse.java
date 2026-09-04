package com.ektrepha.auth.dto.response;

import com.ektrepha.model.UserType;

public record RegisterResponse(
		Long userId,
		String email,
		String name,
		String phoneNumber,
		UserType role,
		boolean emailVerified,
		boolean phoneVerified,
		String accessToken,
		String refreshToken,
		boolean verificationEmailSent,
		boolean otpSentToPhone) {
}
