package com.ektrepha.hourlycare.dto.response;

import java.math.BigDecimal;

import com.ektrepha.booking.dto.response.LiveInArrangementSummary;
import com.ektrepha.pricing.dto.response.PriceBreakdownResponse;

/**
 * A live-in stay - {@code id} is the anchor day's booking id, paid via the same
 * {@code /hourly-care/bookings/{id}/payment} endpoint as monthly care (it sums the whole series)
 * and polled via the same {@code /hourly-care/bookings/{id}/status}.
 */
public record LiveInBookingResponse(
		Long id,
		String status,
		int onDutyDays,
		BigDecimal totalAmount,
		PriceBreakdownResponse priceBreakdown,
		LiveInArrangementSummary arrangement) {
}
