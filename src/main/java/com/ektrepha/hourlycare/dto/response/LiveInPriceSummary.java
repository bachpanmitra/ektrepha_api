package com.ektrepha.hourlycare.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A live-in stay's price - the zone's flat live-in monthly rate times {@code months}. The weekly
 * off day doesn't reduce it; {@code onDutyDays} is informational.
 */
public record LiveInPriceSummary(
		int months,
		LocalDate startDate,
		LocalDate endDate,
		int onDutyDays,
		BigDecimal monthlyRate,
		BigDecimal subtotal,
		BigDecimal platformFee,
		BigDecimal platformFeeGst,
		BigDecimal total,
		String currency) {
}
