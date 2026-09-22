package com.ektrepha.hourlycare.dto.response;

import java.math.BigDecimal;
import java.time.Instant;

/** The reserved (AWAITING_PAYMENT) booking - id is what the Payment screen pays against. */
public record HourlyCareBookingResponse(
		Long id,
		String status,
		Instant startTime,
		Instant endTime,
		BigDecimal totalAmount) {
}
