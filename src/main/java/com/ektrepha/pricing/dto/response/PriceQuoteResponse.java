package com.ektrepha.pricing.dto.response;

import java.math.BigDecimal;

import com.ektrepha.model.PricingMode;

public record PriceQuoteResponse(
		BigDecimal baseRate,
		PricingMode pricingMode,
		AppliedRuleResponse appliedRule,
		DynamicPricingInfoResponse dynamicPricing,
		BigDecimal combinedMultiplier,
		BigDecimal hours,
		BigDecimal subtotal,
		BigDecimal platformFee,
		BigDecimal total,
		String currency) {
}
