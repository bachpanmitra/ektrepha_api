package com.ektrepha.booking.dto.response;

public record NannySummary(
		Long id,
		String firstName,
		String lastName,
		String photoUrl,
		boolean verified,
		Double ratingAvg,
		Integer reviewCount) {
}
