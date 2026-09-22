package com.ektrepha.hourlycare.dto.response;

import java.math.BigDecimal;

public record PaymentInitiateResponse(
		Long paymentId,
		Long bookingId,
		BigDecimal amount,
		String method,
		String status) {
}
