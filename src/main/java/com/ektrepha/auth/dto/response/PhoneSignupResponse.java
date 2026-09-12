package com.ektrepha.auth.dto.response;

import com.ektrepha.model.UserType;

public record PhoneSignupResponse(
		Long userId,
		String phoneNumber,
		UserType role,
		String accessToken,
		String refreshToken) {
}
