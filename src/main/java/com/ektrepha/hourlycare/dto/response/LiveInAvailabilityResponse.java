package com.ektrepha.hourlycare.dto.response;

/** Live-in care Review screen's "Care available" banner + price for the whole stay. */
public record LiveInAvailabilityResponse(
		boolean available,
		LiveInPriceSummary summary,
		String unavailableReason) {
}
