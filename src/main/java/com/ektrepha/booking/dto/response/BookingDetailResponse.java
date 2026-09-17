package com.ektrepha.booking.dto.response;

import java.math.BigDecimal;
import java.time.Instant;

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
		ReviewSummaryResponse review) {
}
