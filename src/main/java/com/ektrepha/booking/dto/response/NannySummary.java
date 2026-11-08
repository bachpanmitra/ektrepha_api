package com.ektrepha.booking.dto.response;

import java.util.List;

public record NannySummary(
		Long id,
		String firstName,
		String lastName,
		String photoUrl,
		boolean verified,
		/** Short, parent-facing labels like "ID Verified" / "Police Verified" — never the documents or doc numbers themselves. See {@code VerificationBadgeMapper}. */
		List<String> verificationBadges,
		Double ratingAvg,
		Integer reviewCount) {
}
