package com.ektrepha.pricing.dto.response;

import java.math.BigDecimal;
import java.util.List;

import com.ektrepha.model.PricingMode;

/** One area's pricing row for a given service type — present (non-null pricing fields) or still unconfigured, for the admin bulk rate-entry screen. */
public record ZoneServicePricingRowResponse(
		Long zoneId,
		String zoneName,
		String city,
		String state,
		boolean zoneActive,
		List<String> pincodes,
		Long pricingId,
		PricingMode pricingMode,
		BigDecimal fixPrice,
		BigDecimal rateMin,
		BigDecimal rateMax,
		BigDecimal unitPrice,
		BigDecimal monthlyPrice,
		String currency,
		BigDecimal minBookingHours,
		BigDecimal platformFeePct,
		boolean pricingActive) {
}
