package com.ektrepha.admin.dto.response;

import java.time.Instant;
import java.util.List;

public record AdminParentDetailResponse(
		Long id,
		String name,
		String email,
		String phone,
		boolean emailVerified,
		boolean phoneVerified,
		long childrenCount,
		long bookingsCount,
		Instant createdAt,
		List<AdminBookingSummaryResponse> recentBookings) {
}
