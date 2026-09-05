package com.ektrepha.auth.dto.response;

import com.ektrepha.model.UserType;

public record GoogleLoginResponse(
		Long userId,
		String email,
		String name,
		UserType role,
		String accessToken,
		String refreshToken) {
}
