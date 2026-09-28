package com.ektrepha.hourlycare.dto.response;

/** Review screen's "Care available" banner + total price breakdown for the whole series. */
public record MonthlyAvailabilityResponse(
		boolean available,
		MonthlyPriceSummary summary,
		String unavailableReason) {
}
