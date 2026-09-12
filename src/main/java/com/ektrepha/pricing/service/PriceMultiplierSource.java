package com.ektrepha.pricing.service;

import java.math.BigDecimal;

import com.ektrepha.model.ZonePricingRule;
import com.ektrepha.model.ZoneServicePricing;

/**
 * Strategy pattern, same shape as {@code nannysearch.service.RankingFactorScorer}: Spring collects
 * every implementing bean into a list and {@code PricingServiceImpl} multiplies all of their
 * results together (then clamps to {@code app.pricing.combined-multiplier-cap}) — adding a third
 * surge source (e.g. a promo code) later is a new bean here, not a change to the pricing service.
 */
public interface PriceMultiplierSource {

	String name();

	/** Returns this source's contribution to the combined multiplier — 1.0 if it doesn't apply. */
	BigDecimal resolve(MultiplierContext context);

	record MultiplierContext(ZoneServicePricing pricing, ZonePricingRule applicableRule) {
	}

}
