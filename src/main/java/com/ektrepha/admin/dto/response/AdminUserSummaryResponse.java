package com.ektrepha.admin.dto.response;

import java.time.Instant;

public record AdminUserSummaryResponse(
		Long id,
		String name,
		String email,
		boolean active,
		boolean isSelf,
		Instant createdAt) {
}
