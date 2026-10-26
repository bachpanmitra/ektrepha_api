package com.ektrepha.pricing.dto.request;

import java.math.BigDecimal;

import com.ektrepha.model.PricingMode;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

public record ZonePricingCreateRequest(
		@NotNull Long serviceTypeId,
		@NotNull PricingMode pricingMode,
		@DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal fixPrice,
		@DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal rateMin,
		@DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal rateMax,
		@DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal unitPrice,
		@DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal monthlyPrice,
		@DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal liveInMonthlyPrice,
		String currency,
		BigDecimal minBookingHours,
		@DecimalMin("0.00") @Digits(integer = 3, fraction = 2) BigDecimal platformFeePct) {
}
