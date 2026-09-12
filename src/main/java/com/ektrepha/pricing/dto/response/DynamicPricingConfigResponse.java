package com.ektrepha.pricing.dto.response;

import java.math.BigDecimal;

public record DynamicPricingConfigResponse(
		Long zoneServicePricingId,
		boolean enabled,
		BigDecimal demandThresholdLow,
		BigDecimal demandThresholdHigh,
		BigDecimal minMultiplier,
		BigDecimal maxMultiplier,
		Integer recomputeIntervalMins) {
}
