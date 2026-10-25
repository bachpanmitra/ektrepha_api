package com.ektrepha.booking.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.ektrepha.parent.dto.response.AddressResponse;
import com.ektrepha.pricing.dto.response.PriceBreakdownLineResponse;

/**
 * {@code priceBreakdown} is the itemized subtotal/platform-fee/GST breakdown behind
 * {@code totalAmount} - a flat, ordered list of labeled lines (zero-amount lines omitted),
 * matching {@code screens/bookings.tsx}'s PaymentCard contract exactly.
 */
public record BookingDetailResponse(
		Long id,
		String status,
		NannySummary nanny,
		ChildSummary child,
		String serviceTypeCode,
		Instant startTime,
		Instant endTime,
		// When the booking itself was placed — distinct from startTime/endTime (the care session).
		Instant createdAt,
		Integer durationHours,
		AddressResponse address,
		BigDecimal totalAmount,
		Long elapsedSeconds,
		ReviewSummaryResponse review,
		// Server-computed, not derived client-side from status — PRD: "if the client derives that
		// from a status string, the two apps drift." Values: "CANCEL", "CONTACT".
		List<String> availableActions,
		// ONE_TIME/REPEAT_DAILY/REPEAT_WEEKLY/REPEAT_MONTHLY.
		String frequency,
		// Total occurrences in this booking's recurring series, or null for a ONE_TIME booking.
		Integer totalOccurrences,
		List<PriceBreakdownLineResponse> priceBreakdown,
		// Elder Care counterpart of child above - null for a childcare booking.
		ElderBookingSummary elder) {
}
