package com.ektrepha.pricing.dto.request;

import java.time.LocalDate;
import java.time.LocalTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PriceCalculationRequest(
		@NotNull Long zoneAreaId,
		@NotBlank String serviceTypeCode,
		@NotNull LocalDate bookingDate,
		@NotNull LocalTime startTime,
		@NotNull LocalTime endTime,
		Long caregiverId) {
}
