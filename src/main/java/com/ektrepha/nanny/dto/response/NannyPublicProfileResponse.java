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
		List<String> skills,
		List<String> languages,
		List<PublicReviewResponse> recentReviews) {
}
