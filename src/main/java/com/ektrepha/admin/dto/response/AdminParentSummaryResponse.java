package com.ektrepha.admin.dto.response;

import java.time.Instant;

public record AdminParentSummaryResponse(
		Long id,
		String name,
		String email,
		String phone,
		long childrenCount,
		long bookingsCount,
		Instant createdAt) {
}
