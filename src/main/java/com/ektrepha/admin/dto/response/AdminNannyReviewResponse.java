package com.ektrepha.admin.dto.response;

import java.time.Instant;

/** {@code moderatedByName}/{@code moderationReason}/{@code moderatedAt} are null until an admin has
 * hidden or restored this review at least once. */
public record AdminNannyReviewResponse(
		Long id,
		Long bookingId,
		String parentName,
		short rating,
		String comment,
		Instant createdAt,
		String status,
		String moderationReason,
		String moderatedByName,
		Instant moderatedAt) {
}
