package com.ektrepha.account.dto.response;

public record LoginMethodsResponse(
		boolean googleConnected,
		String googleEmail,
		boolean phoneVerified,
		String phone,
		boolean emailVerified,
		String email,
		boolean passwordSet) {
}
