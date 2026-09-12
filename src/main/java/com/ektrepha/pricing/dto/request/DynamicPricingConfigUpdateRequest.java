package com.ektrepha.pricing.dto.request;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotNull;

public record DynamicPricingConfigUpdateRequest(
		@NotNull Boolean isEnabled,
		@NotNull BigDecimal demandThresholdLow,
		@NotNull BigDecimal demandThresholdHigh,
		@NotNull BigDecimal minMultiplier,
		@NotNull BigDecimal maxMultiplier,
		Integer recomputeIntervalMins) {
}
