package com.ektrepha.hourlycare.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * The reserved (AWAITING_PAYMENT) series - {@code id} is the anchor occurrence's booking id, and is
 * what the client pays against via the same {@code /hourly-care/bookings/{id}/payment} endpoint
 * hourly/daily care already uses (it sums every occurrence in the series once it sees one).
 */
public record MonthlyBookingResponse(
		Long id,
		String status,
		LocalDate startDate,
		LocalDate endDate,
		int occurrenceCount,
		BigDecimal totalAmount) {
}
