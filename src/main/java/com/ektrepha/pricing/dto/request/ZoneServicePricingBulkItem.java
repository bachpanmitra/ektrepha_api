package com.ektrepha.pricing.dto.request;

import java.math.BigDecimal;

import com.ektrepha.model.PricingMode;

import jakarta.validation.constraints.NotNull;

/** One area's edited rate within a bulk save — created if the area has no pricing row yet for this service type, updated otherwise. */
public record ZoneServicePricingBulkItem(
		@NotNull Long zoneId,
		@NotNull PricingMode pricingMode,
		BigDecimal fixPrice,
		BigDecimal rateMin,
		BigDecimal rateMax,
		BigDecimal unitPrice,
		BigDecimal monthlyPrice,
		String currency,
		BigDecimal minBookingHours,
		BigDecimal platformFeePct) {
}
