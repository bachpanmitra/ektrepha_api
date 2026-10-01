package com.ektrepha.admin.dto.response;

import java.time.Instant;

public record AdminShiftChangeRequestResponse(
		Long id,
		Long bookingId,
		Long nannyId,
		String nannyName,
		String reason,
		String status,
		Instant reviewedAt,
		String rejectionReason,
		Instant createdAt) {
}
