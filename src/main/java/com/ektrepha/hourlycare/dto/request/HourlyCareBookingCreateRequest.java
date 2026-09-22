package com.ektrepha.hourlycare.dto.request;

import java.time.Instant;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Review screen's "Continue to payment" - re-validates availability and reserves the slot as AWAITING_PAYMENT. */
public record HourlyCareBookingCreateRequest(
		@NotNull Long childId,
		@NotNull Long addressId,
		@NotNull Instant startTime,
		@NotNull Instant endTime,
		@Size(max = 500) String careNotes) {
}
