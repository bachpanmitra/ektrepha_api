package com.ektrepha.payment.dto.response;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One row of the Payments screen (ektrepha_ui's L6) — a single payment attempt against a
 * booking, newest first. {@code status} is PAID/FAILED/PROCESSING (payment_transaction.status
 * rephrased for display) or REFUND_PENDING when a SUCCESS attempt's booking was later cancelled.
 */
public record PaymentHistoryItemResponse(
		Long transactionId,
		Long bookingId,
		String frequency,
		String serviceTypeCode,
		String recipientName,
		boolean isElderCare,
		Instant date,
		BigDecimal amount,
		String method,
		String status) {
}
