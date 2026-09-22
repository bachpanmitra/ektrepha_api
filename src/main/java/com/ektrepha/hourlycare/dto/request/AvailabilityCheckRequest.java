package com.ektrepha.hourlycare.dto.request;

import java.time.Instant;

import jakarta.validation.constraints.NotNull;

/** Details screen's "Check availability" - always childcare, never a nanny picked. */
public record AvailabilityCheckRequest(
		@NotNull Long childId,
		@NotNull Long addressId,
		@NotNull Instant startTime,
		@NotNull Instant endTime) {
}
