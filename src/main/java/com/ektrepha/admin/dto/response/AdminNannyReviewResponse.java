package com.ektrepha.admin.dto.response;

import java.time.Instant;

public record AdminNannyReviewResponse(
		Long id,
		Long bookingId,
		String parentName,
		short rating,
		String comment,
		Instant createdAt) {
}
