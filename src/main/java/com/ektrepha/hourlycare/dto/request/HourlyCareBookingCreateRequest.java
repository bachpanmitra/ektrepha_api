package com.ektrepha.hourlycare.dto.request;

import java.time.Instant;
import java.util.UUID;

import com.ektrepha.model.PricingMode;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Review screen's "Continue to payment" - re-validates availability and reserves the slot as AWAITING_PAYMENT.
 * {@code childId} is optional, {@code pricingMode} is RANGE/FIXED - see {@link AvailabilityCheckRequest}.
 * {@code quoteId} is optional (from {@code POST /hourly-care/bookings/quote}) - present and still
 * valid, the quote's locked price (including any trial discount) is honored; absent, the price is
 * recomputed live with no trial discount, same as before this field existed. Keeping it optional
 * means an app build that predates the quote endpoint keeps working unchanged.
 */
public record HourlyCareBookingCreateRequest(
		Long childId,
		@NotNull Long addressId,
		@NotNull Instant startTime,
		@NotNull Instant endTime,
		@Size(max = 500) String careNotes,
		@NotNull PricingMode pricingMode,
		UUID quoteId) {
}
