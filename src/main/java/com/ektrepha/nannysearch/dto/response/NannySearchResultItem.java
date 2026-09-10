package com.ektrepha.nannysearch.dto.response;

import java.math.BigDecimal;

/**
 * {@code profilePhotoUrl} is omitted (null) in M1 — no S3 presigning utility exists in this
 * codebase yet; see plan open question 4. {@code distanceM} is meters, per the "Nanny Proximity
 * Search" PRD's specified response unit — the client formats it as "X km away".
 */
public record NannySearchResultItem(
		Long nannyId,
		String firstName,
		String lastName,
		String profilePhotoUrl,
		BigDecimal hourlyRate,
		Integer yearsExperience,
		String educationLevel,
		double distanceM,
		double matchScore) {
}
