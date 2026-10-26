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
import com.ektrepha.pricing.GstSplitUtil;
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
		PricingMode requestedMode = request.requestedMode();
		requireModeConfigured(pricing, requestedMode);

		DayType dayType = dayTypeResolver.resolve(request.bookingDate(), pricing.getZoneArea().getState());
		// Day-type/time rules are hourly-pricing machinery (a weekend or holiday window within a
		// single day) and have no meaning for a flat monthly retainer (visiting or live-in), so a
		// monthly request never looks one up — no rule ever applies to it.
		ZonePricingRule rule = requestedMode != PricingMode.MONTHLY && requestedMode != PricingMode.LIVE_IN
				? ruleRepository.findApplicableRules(pricing.getId(), dayType, request.startTime(), request.endTime()).stream().findFirst().orElse(null)
				: null;

		BigDecimal hours = enforceMinimumHours(calculateHours(request.startTime(), request.endTime()), pricing.getMinBookingHours());

		BigDecimal baseRate;
		BigDecimal subtotal;
		BigDecimal combinedMultiplier = null;

		if (requestedMode == PricingMode.FIXED) {
			baseRate = (rule != null && rule.getAdjustedFixPrice() != null) ? rule.getAdjustedFixPrice() : pricing.getFixPrice();
			subtotal = baseRate;
		} else if (requestedMode == PricingMode.MONTHLY) {
			// A flat recurring rate — independent of booking hours, day-type rules, and demand
			// surge, none of which have meaning for a monthly retainer.
			baseRate = pricing.getMonthlyPrice();
			subtotal = baseRate;
		} else if (requestedMode == PricingMode.LIVE_IN) {
			// One month of a live-in helper - flat for the same reasons as MONTHLY above. A
			// multi-month stay multiplies this per-month quote (HourlyCareServiceImpl#quoteLiveIn).
			baseRate = pricing.getLiveInMonthlyPrice();
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
			baseRate = GstSplitUtil.scale(effectiveRate.multiply(combinedMultiplier));
			subtotal = GstSplitUtil.scale(baseRate.multiply(hours));
		}

		BigDecimal gstRatePct = appProperties.pricing().platformFeeGstPct();
		// Dividing by 100 is always exact (never non-terminating), so the shared scale() helper
		// alone handles the rounding here - no need for divide's own 3-arg (scale, mode) form.
		BigDecimal platformFee = GstSplitUtil.scale(subtotal.multiply(pricing.getPlatformFeePct())
				.divide(BigDecimal.valueOf(100)));
		// app.pricing.platform-fee-gst-pct (18, matching SAC 999351). A FIRST5_FEE_WAIVER trial
		// waives this along with platformFee itself (see com.ektrepha.trial) - re-applied by the
		// caller below, since this method is parent-agnostic and has no notion of a trial.
		BigDecimal platformFeeGst = GstSplitUtil.scale(platformFee.multiply(gstRatePct)
				.divide(BigDecimal.valueOf(100)));
		BigDecimal total = subtotal.add(platformFee).add(platformFeeGst);

		AppliedRuleResponse appliedRule = rule != null ? new AppliedRuleResponse(rule.getDayType(), rule.getPriceMultiplier()) : null;

		return new PriceQuoteResponse(baseRate, requestedMode, appliedRule, buildDynamicInfo(pricing),
				combinedMultiplier, hours, subtotal, platformFee, platformFeeGst, gstRatePct, total, pricing.getCurrency(),
				false, false);
	}

	// Hourly, daily, monthly and live-in prices are all independently optional on a zone+service row (an
	// admin configures whichever products that zone actually offers), so "not configured" needs its
	// own clean error instead of surfacing as an NPE deep in the branch above.
	private void requireModeConfigured(ZoneServicePricing pricing, PricingMode requestedMode) {
		boolean configured = switch (requestedMode) {
			case FIXED -> pricing.getFixPrice() != null;
			case RANGE -> pricing.getRateMin() != null && pricing.getRateMax() != null;
			case MONTHLY -> pricing.getMonthlyPrice() != null;
			case LIVE_IN -> pricing.getLiveInMonthlyPrice() != null;
		};
		if (!configured) {
			throw new NotServiceableException("This zone/service does not offer " + requestedMode.name().toLowerCase() + " pricing");
		}
	}

	private BigDecimal calculateHours(LocalTime start, LocalTime end) {
		long minutes = Duration.between(start, end).toMinutes();
		// minutes/60 isn't always exact (e.g. 50/60), so divide at higher precision first, then
		// let the shared helper apply the final 2-decimal HALF_UP rounding everything else uses.
		return GstSplitUtil.scale(BigDecimal.valueOf(minutes).divide(BigDecimal.valueOf(60), 10, RoundingMode.HALF_UP));
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
		return GstSplitUtil.scale(pricing.getRateMin().add(pricing.getRateMax()).divide(BigDecimal.valueOf(2)));
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
