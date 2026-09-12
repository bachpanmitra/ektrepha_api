package com.ektrepha.pricing.impl;

import java.math.BigDecimal;

import org.springframework.stereotype.Component;

import com.ektrepha.model.ZonePricingRule;
import com.ektrepha.pricing.service.PriceMultiplierSource;

@Component
public class StaticRuleMultiplierSource implements PriceMultiplierSource {

	@Override
	public String name() {
		return "static-rule";
	}

	@Override
	public BigDecimal resolve(MultiplierContext context) {
		ZonePricingRule rule = context.applicableRule();
		return rule != null ? rule.getPriceMultiplier() : BigDecimal.ONE;
	}

}
