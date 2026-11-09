package com.ektrepha.admin.dto.response;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Today's "on the way"/arrival status for a booking, for ops visibility. Derived the same way as
 * the nanny's and parent's own views (see {@code NannyTripServiceImpl}) — never stored directly,
 * just today's {@code booking_event} rows for TRIP_STARTED/ARRIVED. {@code status} is one of
 * NOT_STARTED, ON_THE_WAY, ARRIVED.
 */
public record AdminTripStatusResponse(
		Long bookingId,
		LocalDate date,
		String status,
		Instant tripStartedAt,
		Instant arrivedAt,
		Double lat,
		Double lng) {
}
