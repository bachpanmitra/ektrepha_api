package com.ektrepha.admin.dto.response;

import java.time.Instant;
import java.time.LocalDate;

public record AdminLeaveRequestResponse(
		Long id,
		Long nannyId,
		String nannyName,
		LocalDate startDate,
		LocalDate endDate,
		String reason,
		String status,
		Instant reviewedAt,
		String rejectionReason,
		Instant createdAt) {
}
