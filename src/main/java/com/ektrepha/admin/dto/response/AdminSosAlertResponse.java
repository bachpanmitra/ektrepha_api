package com.ektrepha.admin.dto.response;

import java.time.Instant;

public record AdminSosAlertResponse(
		Long id,
		Long bookingId,
		Long nannyId,
		String nannyName,
		String nannyPhone,
		Double lat,
		Double lng,
		String notes,
		Instant acknowledgedAt,
		String acknowledgedByName,
		Instant resolvedAt,
		String resolvedByName,
		Instant createdAt) {
}
