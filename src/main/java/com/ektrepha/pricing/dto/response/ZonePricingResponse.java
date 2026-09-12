package com.ektrepha.pricing.dto.response;

import java.math.BigDecimal;

import com.ektrepha.model.PricingMode;

public record ZonePricingResponse(
		Long id,
		Long zoneAreaId,
		Long serviceTypeId,
		String serviceTypeCode,
		PricingMode pricingMode,
		BigDecimal fixPrice,
		BigDecimal rateMin,
		BigDecimal rateMax,
		BigDecimal unitPrice,
		String currency,
		BigDecimal minBookingHours,
		BigDecimal platformFeePct,
		boolean active) {
}
