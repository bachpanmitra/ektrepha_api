package com.ektrepha.pricing.dto.request;

import java.math.BigDecimal;

import com.ektrepha.model.PricingMode;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

/** One area's edited rate within a bulk save — created if the area has no pricing row yet for this service type, updated otherwise. */
public record ZoneServicePricingBulkItem(
		@NotNull Long zoneId,
		@NotNull PricingMode pricingMode,
		@DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal fixPrice,
		@DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal rateMin,
		@DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal rateMax,
		@DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal unitPrice,
		@DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal monthlyPrice,
		// Live-in care (migration 048). On update/bulk save, null keeps the row's existing value
		// rather than clearing it, so an admin client that doesn't send this field yet can't wipe it.
		@DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal liveInMonthlyPrice,
		String currency,
		BigDecimal minBookingHours,
		@DecimalMin("0.00") @Digits(integer = 3, fraction = 2) BigDecimal platformFeePct) {
}
