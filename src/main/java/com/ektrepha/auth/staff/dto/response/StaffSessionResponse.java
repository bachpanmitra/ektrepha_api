package com.ektrepha.auth.staff.dto.response;

/** Matches the nanny app's {@code Session} type (ektrepha-nanny-ui/api/client.ts) exactly — no extra fields, unlike the parent app's mobile-otp response. */
public record StaffSessionResponse(
		Long userId,
		String phone,
		String accessToken,
		String refreshToken) {
}
