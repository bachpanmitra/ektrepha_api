package com.ektrepha.booking.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.ektrepha.parent.dto.response.AddressResponse;

/**
 * {@code totalAmount} is the only money figure available — there is no itemized base/surge/platform-fee
 * breakdown here because no payment_transaction model exists yet (PRD "PRD API Design Spec" conflict #4,
 * out of scope for this build pass). Showing a fabricated breakdown would be worse than showing none.
 */
public record BookingDetailResponse(
		Long id,
		String status,
		NannySummary nanny,
		ChildSummary child,
		String serviceTypeCode,
		Instant startTime,
		Instant endTime,
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
		Integer totalOccurrences) {
}
