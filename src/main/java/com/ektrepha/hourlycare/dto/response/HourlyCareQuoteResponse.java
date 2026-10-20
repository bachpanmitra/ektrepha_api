package com.ektrepha.hourlycare.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * {@code POST /hourly-care/bookings/quote} — a locked, short-lived price (default 15 min) for
 * {@code createBooking} to honor via {@code quoteId}. {@code lineItems} always carries
 * {@code PLATFORM_FEE}/{@code PLATFORM_FEE_GST} (so the app can show the normal fee struck
 * through) and additionally {@code TRIAL_DISCOUNT} (negative) when a FIRST5_FEE_WAIVER trial
 * applies. {@code payablePaise} already nets out the discount; {@code fullPayablePaise} is what
 * it would be without one.
 */
public record HourlyCareQuoteResponse(
		UUID quoteId,
		Instant quoteExpiresAt,
		Instant serverNow,
		List<HourlyCareQuoteLineItem> lineItems,
		long payablePaise,
		long fullPayablePaise,
		String currency) {
}
