package com.ektrepha.pricing.dto.response;

import java.math.BigDecimal;
import java.time.LocalTime;

import com.ektrepha.model.DayType;

public record ZonePricingRuleResponse(
		Long id,
		Long zoneServicePricingId,
		DayType dayType,
		LocalTime startTime,
		LocalTime endTime,
		BigDecimal priceMultiplier,
		BigDecimal adjustedFixPrice,
		Integer priority,
		boolean active) {
}
