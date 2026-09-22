package com.ektrepha.booking.dto.request;

import java.time.Instant;

import com.ektrepha.model.BookingFrequency;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * {@code childId} is required iff the service type resolves to {@code childcare} — enforced in BookingWriteServiceImpl, not here (matches the DB's own app-layer-only enforcement of the same rule, per migration 018's note).
 * <p>
 * {@code frequency} defaults to ONE_TIME when omitted. For a recurring ("book every"/month-base)
 * series, {@code occurrences} is required (2-12) — see BookingWriteServiceImpl#buildOccurrenceWindows.
 */
public record BookingCreateRequest(
		@NotNull Long nannyId,
		@NotNull Long serviceTypeId,
		Long childId,
		@NotNull Long addressId,
		@NotNull Instant startTime,
		@NotNull Instant endTime,
		BookingFrequency frequency,
		@Min(2) @Max(12) Integer occurrences) {
}
