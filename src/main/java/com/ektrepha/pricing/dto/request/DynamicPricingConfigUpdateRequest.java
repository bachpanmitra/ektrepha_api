package com.ektrepha.pricing.dto.request;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

public record DynamicPricingConfigUpdateRequest(
		@NotNull Boolean isEnabled,
		@NotNull @DecimalMin("0.00") @Digits(integer = 2, fraction = 2) BigDecimal demandThresholdLow,
		@NotNull @DecimalMin("0.00") @Digits(integer = 2, fraction = 2) BigDecimal demandThresholdHigh,
		@NotNull @DecimalMin("0.00") @Digits(integer = 2, fraction = 2) BigDecimal minMultiplier,
		@NotNull @DecimalMin("0.00") @Digits(integer = 2, fraction = 2) BigDecimal maxMultiplier,
		Integer recomputeIntervalMins) {
}
