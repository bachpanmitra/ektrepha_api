package com.ektrepha.hourlycare.dto.response;

import java.math.BigDecimal;

/**
 * {@code orderId}/{@code razorpayKeyId} are what the client passes to Razorpay Checkout to collect
 * payment; the client then posts the checkout result back to {@code /payments/{id}/confirm}.
 */
public record PaymentInitiateResponse(
		Long paymentId,
		Long bookingId,
		BigDecimal amount,
		String method,
		String status,
		String orderId,
		String razorpayKeyId) {
}
