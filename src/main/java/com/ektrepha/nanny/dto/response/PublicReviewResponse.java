package com.ektrepha.nanny.dto.response;

import java.time.Instant;

/** {@code parentDisplayName} is "First L." — never the reviewer's full identity. */
public record PublicReviewResponse(
		Long id,
		String parentDisplayName,
		Integer rating,
		String comment,
		Instant createdAt) {
}
