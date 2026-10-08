package com.ektrepha.pricing.dto.request;

import java.time.LocalDate;
import java.time.LocalTime;

import com.ektrepha.model.PricingMode;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * {@code requestedMode} picks which of a zone+service's independently-configured prices to use
 * (hourly range, flat daily, or flat monthly) - a zone can offer all three at once, so the caller
 * must say which product this particular booking is, rather than the row carrying one single
 * active mode. {@link com.ektrepha.pricing.impl.PricingServiceImpl} throws
 * {@link com.ektrepha.exception.NotServiceableException} if that mode's field isn't configured.
 */
public record PriceCalculationRequest(
		@NotNull Long zoneAreaId,
		@NotBlank String serviceTypeCode,
		@NotNull LocalDate bookingDate,
		@NotNull LocalTime startTime,
		@NotNull LocalTime endTime,
		Long caregiverId,
		@NotNull PricingMode requestedMode) {
}
