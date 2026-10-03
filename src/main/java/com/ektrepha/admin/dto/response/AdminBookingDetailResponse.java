package com.ektrepha.admin.dto.response;

import java.math.BigDecimal;
import java.time.Instant;

/** {@code paymentStatus}/{@code paymentReferenceId}/{@code paymentMethod} are null when the booking has no {@code payment_transaction} row — most bookings outside the hourly-care pay-first flow never get one. {@code paymentReferenceId} is the Razorpay gateway payment id ({@code gateway_payment_id}) once settled, falling back to the order id ({@code provider_reference}) while still pending — see migration 035. */
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
		String paymentReferenceId,
		String paymentMethod,
		AdminParentRefResponse parent,
		AdminNannyRefResponse nanny,
		AdminChildRefResponse child,
		AdminAddressResponse address,
		Instant createdAt,
		Instant checkedInAt,
		Instant checkedOutAt) {
}
