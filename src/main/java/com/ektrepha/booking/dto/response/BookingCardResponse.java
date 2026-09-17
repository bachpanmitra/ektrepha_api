package com.ektrepha.booking.dto.response;

import java.math.BigDecimal;
import java.time.Instant;

/** {@code elapsedSeconds} is non-null only when status is IN_PROGRESS, and is always computed from the server clock — never trust a device's clock for "01:24 elapsed" on B3. */
public record BookingCardResponse(
		Long id,
		String status,
		NannySummary nanny,
		ChildSummary child,
		Instant startTime,
		Instant endTime,
		String serviceTypeCode,
		BigDecimal totalAmount,
		Long elapsedSeconds,
		boolean reviewPending) {
}
