package com.ektrepha.hourlycare.dto.response;

import com.ektrepha.pricing.dto.response.PriceQuoteResponse;

/** Review screen's "Care available" banner + price breakdown - {@code priceQuote} is null when unavailable. */
public record AvailabilityResponse(
		boolean available,
		PriceQuoteResponse priceQuote,
		String unavailableReason) {
}
