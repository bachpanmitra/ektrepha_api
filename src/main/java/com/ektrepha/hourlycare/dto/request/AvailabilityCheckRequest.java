package com.ektrepha.hourlycare.dto.request;

import java.time.Instant;

import jakarta.validation.constraints.NotNull;

/**
 * Details screen's "Check availability" - always childcare, never a nanny picked.
 * {@code childId} is optional - the parent can skip child details and add them later from My
 * bookings (see {@link com.ektrepha.model.Booking#getChild()}, already nullable at this layer).
 */
public record AvailabilityCheckRequest(
		Long childId,
		@NotNull Long addressId,
		@NotNull Instant startTime,
		@NotNull Instant endTime) {
}
