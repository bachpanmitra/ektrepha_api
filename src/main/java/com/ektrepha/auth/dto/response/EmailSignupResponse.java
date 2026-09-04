package com.ektrepha.auth.dto.response;

public record EmailSignupResponse(
		Long userId,
		String email,
		boolean emailVerified,
		String accessToken,
		String refreshToken,
		boolean verificationEmailSent) {
}
