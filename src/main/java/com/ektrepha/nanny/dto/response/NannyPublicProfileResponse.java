package com.ektrepha.nanny.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record NannyPublicProfileResponse(
		Long id,
		String firstName,
		String lastName,
		String photoUrl,
		String bio,
		Integer yearsExperience,
		String educationLevel,
		BigDecimal hourlyRate,
		Double ratingAvg,
		Integer reviewCount,
		boolean verified,
		/** Short, parent-facing labels like "ID Verified" / "Police Verified" — never the documents or doc numbers themselves. See {@code VerificationBadgeMapper}. */
		List<String> verificationBadges,
		List<String> skills,
		List<String> languages,
		List<PublicReviewResponse> recentReviews) {
}
