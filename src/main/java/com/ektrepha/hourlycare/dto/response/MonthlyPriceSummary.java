package com.ektrepha.hourlycare.dto.response;

import java.math.BigDecimal;

/**
 * The sum of every occurrence's own quote — each day can carry a different rate (weekday/weekend/
 * demand pricing), so this is a total across {@code occurrenceCount} real per-day quotes, not one
 * quote for the series as if it were a single continuous booking.
 */
public record MonthlyPriceSummary(
		int occurrenceCount,
		BigDecimal subtotal,
		BigDecimal platformFee,
		BigDecimal total,
		String currency) {
}
