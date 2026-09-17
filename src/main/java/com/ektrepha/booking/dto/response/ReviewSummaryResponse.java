package com.ektrepha.booking.dto.response;

import java.time.Instant;

/** The parent's own review on one of their bookings (H2) — no display-name anonymization needed, unlike S1's public review list. */
public record ReviewSummaryResponse(
		Long id,
		Integer rating,
		String comment,
		Instant createdAt) {
}
