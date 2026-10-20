package com.ektrepha.hourlycare.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.ektrepha.pricing.dto.response.PriceBreakdownResponse;

/**
 * The reserved (AWAITING_PAYMENT) series - {@code id} is the anchor occurrence's booking id, and is
 * what the client pays against via the same {@code /hourly-care/bookings/{id}/payment} endpoint
 * hourly/daily care already uses (it sums every occurrence in the series once it sees one).
 * {@code priceBreakdown} is the anchor's own breakdown - the only occurrence actually billed (see
 * HourlyCareServiceImpl#createMonthlyBooking's flat-rate "bill once" handling).
 */
public record MonthlyBookingResponse(
		Long id,
		String status,
		LocalDate startDate,
		LocalDate endDate,
		int occurrenceCount,
		BigDecimal totalAmount,
		PriceBreakdownResponse priceBreakdown) {
}
