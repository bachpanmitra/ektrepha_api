package com.ektrepha.hourlycare.dto.request;

import jakarta.validation.constraints.NotBlank;

/** What Razorpay Checkout hands the client back after a successful charge - verified server-side against the order it was initiated with. */
public record PaymentConfirmRequest(
		@NotBlank String razorpayPaymentId,
		@NotBlank String razorpaySignature) {
}
