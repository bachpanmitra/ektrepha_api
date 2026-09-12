package com.ektrepha.pricing.dto.request;

import java.math.BigDecimal;
import java.time.LocalTime;

import com.ektrepha.model.DayType;

import jakarta.validation.constraints.NotNull;

public record ZonePricingRuleCreateRequest(
		@NotNull DayType dayType,
		@NotNull LocalTime startTime,
		@NotNull LocalTime endTime,
		BigDecimal priceMultiplier,
		BigDecimal adjustedFixPrice,
		Integer priority) {
}
