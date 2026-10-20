package com.ektrepha.hourlycare.dto.request;

import java.time.Instant;

import com.ektrepha.model.PricingMode;

import jakarta.validation.constraints.NotNull;

/**
 * Details screen's "Check availability" - always childcare, never a nanny picked.
 * {@code childId} is optional - the parent can skip child details and add them later from My
 * bookings (see {@link com.ektrepha.model.Booking#getChild()}, already nullable at this layer).
 * {@code pricingMode} says which of the zone's independently-configured products this is - RANGE
 * for the "Hourly care" screen, FIXED for "Daily care" (MONTHLY is monthly-care's own endpoints
 * below, never this one).
 */
public record AvailabilityCheckRequest(
		Long childId,
		@NotNull Long addressId,
		@NotNull Instant startTime,
		@NotNull Instant endTime,
		@NotNull PricingMode pricingMode,
		/**
		 * Advisory only, for client-side UI state (e.g. showing a "free trial" banner before the
		 * price comes back) - never trusted for pricing. The server always re-derives trial
		 * eligibility itself from this parent's own {@code ParentTrial} row (see
		 * {@code HourlyCareServiceImpl#applyDailyCareTrial}), since a client-sent boolean is
		 * trivially spoofable. Null/omitted is treated the same as false - it's simply ignored.
		 */
		Boolean isTrialPeriod) {
}
