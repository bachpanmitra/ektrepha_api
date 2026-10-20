package com.ektrepha.pricing.dto.request;

import java.math.BigDecimal;
import java.time.LocalTime;

import com.ektrepha.model.DayType;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

public record ZonePricingRuleUpdateRequest(
		@NotNull DayType dayType,
		@NotNull LocalTime startTime,
		@NotNull LocalTime endTime,
		@DecimalMin("0.00") @Digits(integer = 2, fraction = 2) BigDecimal priceMultiplier,
		@DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal adjustedFixPrice,
		Integer priority,
		Boolean active) {
}
