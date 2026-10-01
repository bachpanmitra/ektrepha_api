package com.ektrepha.admin.dto.response;

import java.time.Instant;

public record AdminAttendanceCorrectionResponse(
		Long id,
		Long bookingId,
		Long nannyId,
		String nannyName,
		String details,
		String status,
		Instant reviewedAt,
		String rejectionReason,
		Instant createdAt) {
}
