package com.ektrepha.auth.dto.response;

import com.ektrepha.model.UserType;

public record EmailLoginResponse(
		Long userId,
		String email,
		UserType role,
		String accessToken,
		String refreshToken) {
}
