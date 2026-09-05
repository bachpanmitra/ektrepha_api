package com.ektrepha.auth.dto.response;

import com.ektrepha.model.UserType;

public record GoogleSignupResponse(
		Long userId,
		String email,
		String name,
		UserType role,
		String accessToken,
		String refreshToken,
		boolean isNewUser,
		boolean passwordSetupEmailSent) {
}
