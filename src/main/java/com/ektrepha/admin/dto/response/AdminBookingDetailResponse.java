package com.ektrepha.admin.dto.response;

import java.math.BigDecimal;
import java.time.Instant;

/** {@code paymentStatus} is null when the booking has no {@code payment_transaction} row — most bookings outside the hourly-care pay-first flow never get one. */
public record AdminBookingDetailResponse(
		Long id,
		String serviceTypeCode,
		String serviceTypeName,
		String status,
		Instant startTime,
		Instant endTime,
		String careNotes,
		BigDecimal totalAmount,
		String paymentStatus,
		AdminParentRefResponse parent,
		AdminNannyRefResponse nanny,
		AdminChildRefResponse child,
		AdminAddressResponse address,
		Instant createdAt,
		Instant checkedInAt,
		Instant checkedOutAt) {
}
