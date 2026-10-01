package com.ektrepha.admin.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record AdminNannySummaryResponse(
		Long id,
		String name,
		String phone,
		String email,
		String verificationStatus,
		boolean active,
		BigDecimal hourlyRate,
		List<String> zoneNames,
		Instant createdAt) {
}
