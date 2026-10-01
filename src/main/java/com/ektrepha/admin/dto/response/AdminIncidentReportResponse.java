package com.ektrepha.admin.dto.response;

import java.time.Instant;

public record AdminIncidentReportResponse(
		Long id,
		Long bookingId,
		Long nannyId,
		String nannyName,
		String reportedByName,
		String description,
		String status,
		Instant resolvedAt,
		String resolutionNotes,
		Instant createdAt) {
}
