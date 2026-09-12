package com.ektrepha.pricing.impl;

import java.math.BigDecimal;

import org.springframework.stereotype.Component;

import com.ektrepha.model.DynamicPricingConfig;
import com.ektrepha.model.ZoneDemandSnapshot;
import com.ektrepha.model.ZoneServicePricing;
import com.ektrepha.pricing.service.PriceMultiplierSource;
import com.ektrepha.repository.DynamicPricingConfigRepository;
import com.ektrepha.repository.ZoneDemandSnapshotRepository;

import lombok.RequiredArgsConstructor;

/** No config row, disabled, or no snapshot yet (just enabled, before the first recompute tick) all degrade to 1.0 — a missing signal must never silently inflate price. */
@Component
@RequiredArgsConstructor
public class DemandMultiplierSource implements PriceMultiplierSource {

	private final DynamicPricingConfigRepository dynamicPricingConfigRepository;
	private final ZoneDemandSnapshotRepository zoneDemandSnapshotRepository;

	@Override
	public String name() {
		return "demand";
	}

	@Override
	public BigDecimal resolve(MultiplierContext context) {
		ZoneServicePricing pricing = context.pricing();
		boolean enabled = dynamicPricingConfigRepository.findByZoneServicePricingId(pricing.getId())
				.map(DynamicPricingConfig::isEnabled)
				.orElse(false);
		if (!enabled) {
			return BigDecimal.ONE;
		}
		return zoneDemandSnapshotRepository.findByZoneAreaIdAndServiceTypeId(pricing.getZoneArea().getId(), pricing.getServiceType().getId())
				.map(ZoneDemandSnapshot::getComputedMultiplier)
				.orElse(BigDecimal.ONE);
	}

}
