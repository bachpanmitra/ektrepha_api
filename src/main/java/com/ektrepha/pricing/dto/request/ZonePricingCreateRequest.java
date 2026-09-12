package com.ektrepha.pricing.dto.request;

import java.math.BigDecimal;

import com.ektrepha.model.PricingMode;

import jakarta.validation.constraints.NotNull;

public record ZonePricingCreateRequest(
		@NotNull Long serviceTypeId,
		@NotNull PricingMode pricingMode,
		BigDecimal fixPrice,
		BigDecimal rateMin,
		BigDecimal rateMax,
		BigDecimal unitPrice,
		String currency,
		BigDecimal minBookingHours,
		BigDecimal platformFeePct) {
}
