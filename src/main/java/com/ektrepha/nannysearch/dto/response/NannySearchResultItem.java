package com.ektrepha.nannysearch.dto.response;

import java.math.BigDecimal;

/** {@code profilePhotoUrl} is omitted (null) in M1 — no S3 presigning utility exists in this codebase yet; see plan open question 4. */
public record NannySearchResultItem(
		Long nannyId,
		String firstName,
		String lastName,
		String profilePhotoUrl,
		BigDecimal hourlyRate,
		Integer yearsExperience,
		String educationLevel,
		double distanceKm,
		double matchScore) {
}
