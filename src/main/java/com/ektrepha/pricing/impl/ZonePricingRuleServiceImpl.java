package com.ektrepha.pricing.impl;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ektrepha.exception.ZonePricingNotFoundException;
import com.ektrepha.exception.ZonePricingRuleNotFoundException;
import com.ektrepha.model.ZonePricingRule;
import com.ektrepha.model.ZoneServicePricing;
import com.ektrepha.pricing.dto.request.ZonePricingRuleCreateRequest;
import com.ektrepha.pricing.dto.request.ZonePricingRuleUpdateRequest;
import com.ektrepha.pricing.dto.response.ZonePricingRuleResponse;
import com.ektrepha.pricing.service.ZonePricingRuleService;
import com.ektrepha.repository.ZonePricingRuleRepository;
import com.ektrepha.repository.ZoneServicePricingRepository;

import lombok.RequiredArgsConstructor;

/** No cache eviction needed here (unlike {@link ZonePricingServiceImpl}) - rule lookups happen fresh on every {@code calculate()} call, never cached, since they're keyed by day-type/time-window, not a simple id. */
@Service
@RequiredArgsConstructor
public class ZonePricingRuleServiceImpl implements ZonePricingRuleService {

	private final ZonePricingRuleRepository ruleRepository;
	private final ZoneServicePricingRepository pricingRepository;

	@Override
	@Transactional(readOnly = true)
	public List<ZonePricingRuleResponse> list(Long zoneServicePricingId) {
		return ruleRepository.findAllByZoneServicePricingIdOrderByPriorityDesc(zoneServicePricingId).stream()
				.map(this::toResponse)
				.toList();
	}

	@Override
	@Transactional
	public ZonePricingRuleResponse create(Long zoneServicePricingId, ZonePricingRuleCreateRequest request) {
		ZoneServicePricing pricing = pricingRepository.findById(zoneServicePricingId)
				.orElseThrow(() -> new ZonePricingNotFoundException("No pricing row with id " + zoneServicePricingId));
		validateWindow(request.startTime(), request.endTime());

		ZonePricingRule rule = ZonePricingRule.builder()
				.zoneServicePricing(pricing)
				.dayType(request.dayType())
				.startTime(request.startTime())
				.endTime(request.endTime())
				.priceMultiplier(request.priceMultiplier() != null ? request.priceMultiplier() : BigDecimal.ONE)
				.adjustedFixPrice(request.adjustedFixPrice())
				.priority(request.priority() != null ? request.priority() : 0)
				.active(true)
				.build();
		return toResponse(ruleRepository.save(rule));
	}

	@Override
	@Transactional
	public ZonePricingRuleResponse update(Long ruleId, ZonePricingRuleUpdateRequest request) {
		ZonePricingRule rule = ruleRepository.findById(ruleId)
				.orElseThrow(() -> new ZonePricingRuleNotFoundException("No pricing rule with id " + ruleId));
		validateWindow(request.startTime(), request.endTime());

		rule.setDayType(request.dayType());
		rule.setStartTime(request.startTime());
		rule.setEndTime(request.endTime());
		if (request.priceMultiplier() != null) {
			rule.setPriceMultiplier(request.priceMultiplier());
		}
		rule.setAdjustedFixPrice(request.adjustedFixPrice());
		if (request.priority() != null) {
			rule.setPriority(request.priority());
		}
		if (request.active() != null) {
			rule.setActive(request.active());
		}
		return toResponse(ruleRepository.save(rule));
	}

	private void validateWindow(LocalTime startTime, LocalTime endTime) {
		if (!endTime.isAfter(startTime)) {
			throw new IllegalArgumentException("endTime must be after startTime");
		}
	}

	private ZonePricingRuleResponse toResponse(ZonePricingRule rule) {
		return new ZonePricingRuleResponse(rule.getId(), rule.getZoneServicePricing().getId(), rule.getDayType(),
				rule.getStartTime(), rule.getEndTime(), rule.getPriceMultiplier(), rule.getAdjustedFixPrice(), rule.getPriority(), rule.isActive());
	}

}
