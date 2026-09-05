package com.ektrepha.nannysearch.dto.response;

import java.time.Instant;

public record ReviewResponse(
		Long id,
		Long bookingId,
		Long nannyId,
		Integer rating,
		String comment,
		Instant createdAt) {
}
