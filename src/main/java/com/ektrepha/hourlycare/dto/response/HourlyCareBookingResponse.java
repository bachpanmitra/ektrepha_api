package com.ektrepha.hourlycare.dto.response;

import java.math.BigDecimal;
import java.time.Instant;

import com.ektrepha.pricing.dto.response.PriceBreakdownResponse;

/** The reserved (AWAITING_PAYMENT) booking - id is what the Payment screen pays against. */
public record HourlyCareBookingResponse(
		Long id,
		String status,
		Instant startTime,
		Instant endTime,
		BigDecimal totalAmount,
		PriceBreakdownResponse priceBreakdown) {
}
