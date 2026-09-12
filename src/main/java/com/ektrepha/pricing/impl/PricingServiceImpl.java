package com.ektrepha.pricing.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ektrepha.config.properties.AppProperties;
import com.ektrepha.exception.NotServiceableException;
import com.ektrepha.exception.ServiceTypeNotFoundException;
import com.ektrepha.model.CaregiverZoneMapping;
import com.ektrepha.model.DayType;
import com.ektrepha.model.DynamicPricingConfig;
import com.ektrepha.model.PricingMode;
import com.ektrepha.model.ServiceType;
import com.ektrepha.model.ZonePricingRule;
import com.ektrepha.model.ZoneServicePricing;
import com.ektrepha.pricing.dto.request.PriceCalculationRequest;
import com.ektrepha.pricing.dto.response.AppliedRuleResponse;
import com.ektrepha.pricing.dto.response.DynamicPricingInfoResponse;
import com.ektrepha.pricing.dto.response.PriceQuoteResponse;
import com.ektrepha.pricing.service.DayTypeResolver;
import com.ektrepha.pricing.service.PriceMultiplierSource;
import com.ektrepha.pricing.service.PriceMultiplierSource.MultiplierContext;
import com.ektrepha.pricing.service.PricingService;
import com.ektrepha.repository.CaregiverZoneMappingRepository;
import com.ektrepha.repository.DynamicPricingConfigRepository;
import com.ektrepha.repository.ServiceTypeRepository;
import com.ektrepha.repository.ZoneDemandSnapshotRepository;
import com.ektrepha.repository.ZonePricingRuleRepository;
import com.ektrepha.repository.ZoneServicePricingRepository;

import lombok.RequiredArgsConstructor;

/** Mirrors the design doc's {@code PricingService.calculate} closely — see class-level comments on each collaborator interface for which GoF pattern covers which seam. */
@Service
@RequiredArgsConstructor
public class PricingServiceImpl implements PricingService {

	private static final int MONEY_SCALE = 2;

	private final ZoneServicePricingRepository pricingRepository;
	private final ServiceTypeRepository serviceTypeRepository;
	private final ZonePricingRuleRepository ruleRepository;
	private final CaregiverZoneMappingRepository caregiverZoneMappingRepository;
	private final DynamicPricingConfigRepository dynamicPricingConfigRepository;
	private final ZoneDemandSnapshotRepository demandSnapshotRepository;
	private final DayTypeResolver dayTypeResolver;
	private final List<PriceMultiplierSource> multiplierSources;
	private final AppProperties appProperties;

	@Override
	@Transactional(readOnly = true)
	public PriceQuoteResponse calculate(PriceCalculationRequest request) {
		if (!request.endTime().isAfter(request.startTime())) {
			throw new IllegalArgumentException("endTime must be after startTime");
		}

		ServiceType serviceType = serviceTypeRepository.findByCode(request.serviceTypeCode())
				.orElseThrow(() -> new ServiceTypeNotFoundException("No service type with code " + request.serviceTypeCode()));
		ZoneServicePricing pricing = pricingRepository.findByZoneAreaIdAndServiceTypeIdAndActiveTrue(request.zoneAreaId(), serviceType.getId())
				.orElseThrow(() -> new NotServiceableException("No active pricing configured for this zone and service type"));

		DayType dayType = dayTypeResolver.resolve(request.bookingDate());
		ZonePricingRule rule = ruleRepository.findApplicableRules(pricing.getId(), dayType, request.startTime(), request.endTime())
				.stream().findFirst().orElse(null);

		BigDecimal hours = enforceMinimumHours(calculateHours(request.startTime(), request.endTime()), pricing.getMinBookingHours());

		BigDecimal baseRate;
		BigDecimal subtotal;
		BigDecimal combinedMultiplier = null;

		if (pricing.getPricingMode() == PricingMode.FIXED) {
			baseRate = (rule != null && rule.getAdjustedFixPrice() != null) ? rule.getAdjustedFixPrice() : pricing.getFixPrice();
			subtotal = baseRate;
		} else {
			BigDecimal effectiveRate = resolveEffectiveRate(request, pricing);
			MultiplierContext context = new MultiplierContext(pricing, rule);
			BigDecimal rawMultiplier = multiplierSources.stream()
					.map(source -> source.resolve(context))
					.reduce(BigDecimal.ONE, BigDecimal::multiply);
			// Hard ceiling regardless of how many surge sources compose — see design doc's
			// "surge pricing news story" guardrail.
			combinedMultiplier = rawMultiplier.min(appProperties.pricing().combinedMultiplierCap());
			baseRate = effectiveRate.multiply(combinedMultiplier).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
			subtotal = baseRate.multiply(hours).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
		}

		BigDecimal platformFee = subtotal.multiply(pricing.getPlatformFeePct())
				.divide(BigDecimal.valueOf(100), MONEY_SCALE, RoundingMode.HALF_UP);
		BigDecimal total = subtotal.add(platformFee);

		AppliedRuleResponse appliedRule = rule != null ? new AppliedRuleResponse(rule.getDayType(), rule.getPriceMultiplier()) : null;

		return new PriceQuoteResponse(baseRate, pricing.getPricingMode(), appliedRule, buildDynamicInfo(pricing),
				combinedMultiplier, hours, subtotal, platformFee, total, pricing.getCurrency());
	}

	private BigDecimal calculateHours(LocalTime start, LocalTime end) {
		long minutes = Duration.between(start, end).toMinutes();
		return BigDecimal.valueOf(minutes).divide(BigDecimal.valueOf(60), MONEY_SCALE, RoundingMode.HALF_UP);
	}

	private BigDecimal enforceMinimumHours(BigDecimal requestedHours, BigDecimal minBookingHours) {
		return requestedHours.compareTo(minBookingHours) < 0 ? minBookingHours : requestedHours;
	}

	// Marketplace model (a caregiver's own declared rate, clamped to the zone's allowed range) if a
	// caregiverId was supplied and they have one; managed model (midpoint of the zone's range)
	// otherwise. Both models coexist rather than picking one, matching the design doc's
	// resolveEffectiveRate exactly - open design question #2 doesn't need to be answered up front.
	private BigDecimal resolveEffectiveRate(PriceCalculationRequest request, ZoneServicePricing pricing) {
		if (request.caregiverId() != null) {
			Optional<CaregiverZoneMapping> mapping = caregiverZoneMappingRepository
					.findByCaregiverIdAndZoneAreaIdAndServiceTypeIdAndActiveTrue(request.caregiverId(), pricing.getZoneArea().getId(), pricing.getServiceType().getId());
			if (mapping.isPresent() && mapping.get().getOwnRate() != null) {
				return clamp(mapping.get().getOwnRate(), pricing.getRateMin(), pricing.getRateMax());
			}
		}
		return pricing.getRateMin().add(pricing.getRateMax()).divide(BigDecimal.valueOf(2), MONEY_SCALE, RoundingMode.HALF_UP);
	}

	private BigDecimal clamp(BigDecimal value, BigDecimal min, BigDecimal max) {
		if (value.compareTo(min) < 0) {
			return min;
		}
		if (value.compareTo(max) > 0) {
			return max;
		}
		return value;
	}

	// Always populated, even for fixed-mode pricing where dynamic surge never touches the price -
	// see DynamicPricingInfoResponse's javadoc on why this stays transparent regardless.
	private DynamicPricingInfoResponse buildDynamicInfo(ZoneServicePricing pricing) {
		Optional<DynamicPricingConfig> config = dynamicPricingConfigRepository.findByZoneServicePricingId(pricing.getId());
		boolean enabled = config.map(DynamicPricingConfig::isEnabled).orElse(false);
		if (!enabled) {
			return new DynamicPricingInfoResponse(false, null, null, null);
		}
		return demandSnapshotRepository.findByZoneAreaIdAndServiceTypeId(pricing.getZoneArea().getId(), pricing.getServiceType().getId())
				.map(snapshot -> new DynamicPricingInfoResponse(true, snapshot.getDemandRatio(), snapshot.getComputedMultiplier(), snapshot.getComputedAt()))
				.orElse(new DynamicPricingInfoResponse(true, null, BigDecimal.ONE, null));
	}

}
