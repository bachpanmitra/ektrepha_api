package com.ektrepha.pricing.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ektrepha.exception.ZonePricingNotFoundException;
import com.ektrepha.model.DynamicPricingConfig;
import com.ektrepha.model.ZoneDemandSnapshot;
import com.ektrepha.model.ZoneDemandSnapshotHistory;
import com.ektrepha.model.ZoneServicePricing;
import com.ektrepha.pricing.dto.request.DynamicPricingConfigUpdateRequest;
import com.ektrepha.pricing.dto.response.DemandSnapshotResponse;
import com.ektrepha.pricing.dto.response.DynamicPricingConfigResponse;
import com.ektrepha.pricing.service.CaregiverDemandSignalSource;
import com.ektrepha.pricing.service.DemandPricingService;
import com.ektrepha.repository.CaregiverZoneMappingRepository;
import com.ektrepha.repository.DynamicPricingConfigRepository;
import com.ektrepha.repository.ZoneDemandSnapshotHistoryRepository;
import com.ektrepha.repository.ZoneDemandSnapshotRepository;
import com.ektrepha.repository.ZoneServicePricingRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class DemandPricingServiceImpl implements DemandPricingService {

	private final ZoneServicePricingRepository pricingRepository;
	private final DynamicPricingConfigRepository configRepository;
	private final ZoneDemandSnapshotRepository snapshotRepository;
	private final ZoneDemandSnapshotHistoryRepository historyRepository;
	private final CaregiverZoneMappingRepository caregiverZoneMappingRepository;
	private final CaregiverDemandSignalSource demandSignalSource;

	@Override
	@Transactional(readOnly = true)
	public DynamicPricingConfigResponse getConfig(Long zoneId, Long serviceTypeId) {
		ZoneServicePricing pricing = findPricing(zoneId, serviceTypeId);
		DynamicPricingConfig config = configRepository.findByZoneServicePricingId(pricing.getId()).orElseGet(() -> defaultConfig(pricing));
		return toConfigResponse(pricing.getId(), config);
	}

	@Override
	@Transactional
	public DynamicPricingConfigResponse updateConfig(Long zoneId, Long serviceTypeId, DynamicPricingConfigUpdateRequest request) {
		ZoneServicePricing pricing = findPricing(zoneId, serviceTypeId);
		DynamicPricingConfig config = configRepository.findByZoneServicePricingId(pricing.getId())
				.orElseGet(() -> DynamicPricingConfig.builder().zoneServicePricing(pricing).build());

		config.setEnabled(request.isEnabled());
		config.setDemandThresholdLow(request.demandThresholdLow());
		config.setDemandThresholdHigh(request.demandThresholdHigh());
		config.setMinMultiplier(request.minMultiplier());
		config.setMaxMultiplier(request.maxMultiplier());
		config.setRecomputeIntervalMins(request.recomputeIntervalMins() != null ? request.recomputeIntervalMins() : 10);

		DynamicPricingConfig saved = configRepository.save(config);
		return toConfigResponse(pricing.getId(), saved);
	}

	@Override
	@Transactional(readOnly = true)
	public DemandSnapshotResponse getSnapshot(Long zoneId, Long serviceTypeId) {
		return snapshotRepository.findByZoneAreaIdAndServiceTypeId(zoneId, serviceTypeId)
				.map(s -> new DemandSnapshotResponse(zoneId, serviceTypeId, s.getOpenBookingRequests(), s.getAvailableCaregivers(),
						s.getDemandRatio(), s.getComputedMultiplier(), s.getComputedAt()))
				.orElse(new DemandSnapshotResponse(zoneId, serviceTypeId, 0, 0, null, BigDecimal.ONE, null));
	}

	// Template-method-shaped: fetch every enabled config, compute one snapshot each, persist
	// current + append history. Stays one method (not a class hierarchy) because there is exactly
	// one recompute algorithm today - nothing to abstract a variation point for yet.
	@Override
	@Transactional
	public void recomputeAll() {
		List<DynamicPricingConfig> configs = configRepository.findAllByEnabledTrue();
		log.debug("Recomputing demand snapshots for {} enabled zone-service configs", configs.size());
		for (DynamicPricingConfig config : configs) {
			recomputeOne(config);
		}
	}

	private void recomputeOne(DynamicPricingConfig config) {
		ZoneServicePricing pricing = config.getZoneServicePricing();
		Long zoneAreaId = pricing.getZoneArea().getId();
		Long serviceTypeId = pricing.getServiceType().getId();

		Instant now = Instant.now();
		int openRequests = demandSignalSource.countOpenBookingRequests(zoneAreaId, serviceTypeId, pricing.getServiceType().getCode());
		int availableCaregivers = caregiverZoneMappingRepository.countAvailableNow(zoneAreaId, serviceTypeId, now);

		BigDecimal demandRatio = computeDemandRatio(openRequests, availableCaregivers, config);
		BigDecimal multiplier = mapRatioToMultiplier(demandRatio, config);

		ZoneDemandSnapshot snapshot = snapshotRepository.findByZoneAreaIdAndServiceTypeId(zoneAreaId, serviceTypeId)
				.orElseGet(() -> ZoneDemandSnapshot.builder().zoneAreaId(zoneAreaId).serviceTypeId(serviceTypeId).build());
		snapshot.setOpenBookingRequests(openRequests);
		snapshot.setAvailableCaregivers(availableCaregivers);
		snapshot.setDemandRatio(demandRatio);
		snapshot.setComputedMultiplier(multiplier);
		snapshot.setComputedAt(now);
		snapshotRepository.save(snapshot);

		historyRepository.save(ZoneDemandSnapshotHistory.builder()
				.zoneAreaId(zoneAreaId)
				.serviceTypeId(serviceTypeId)
				.demandRatio(demandRatio)
				.computedMultiplier(multiplier)
				.computedAt(now)
				.build());
	}

	// No supply + open demand caps straight at max (design doc: "no supply + demand -> cap at
	// max"); zero-and-zero is treated as zero demand, not undefined.
	private BigDecimal computeDemandRatio(int openRequests, int availableCaregivers, DynamicPricingConfig config) {
		if (availableCaregivers == 0) {
			return openRequests > 0 ? config.getMaxMultiplier() : BigDecimal.ZERO;
		}
		return BigDecimal.valueOf(openRequests).divide(BigDecimal.valueOf(availableCaregivers), 3, RoundingMode.HALF_UP);
	}

	private BigDecimal mapRatioToMultiplier(BigDecimal ratio, DynamicPricingConfig config) {
		if (ratio.compareTo(config.getDemandThresholdLow()) <= 0) {
			return config.getMinMultiplier();
		}
		if (ratio.compareTo(config.getDemandThresholdHigh()) >= 0) {
			return config.getMaxMultiplier();
		}
		BigDecimal ratioRange = config.getDemandThresholdHigh().subtract(config.getDemandThresholdLow());
		BigDecimal multiplierRange = config.getMaxMultiplier().subtract(config.getMinMultiplier());
		BigDecimal position = ratio.subtract(config.getDemandThresholdLow()).divide(ratioRange, 4, RoundingMode.HALF_UP);
		return config.getMinMultiplier().add(multiplierRange.multiply(position)).setScale(2, RoundingMode.HALF_UP);
	}

	private ZoneServicePricing findPricing(Long zoneId, Long serviceTypeId) {
		return pricingRepository.findByZoneAreaIdAndServiceTypeId(zoneId, serviceTypeId)
				.orElseThrow(() -> new ZonePricingNotFoundException("No pricing configured for zone " + zoneId + " and service type " + serviceTypeId));
	}

	// Safe do-nothing default (design doc rollout decision #5): a zone with no config row yet
	// reports disabled rather than a config lookup failing outright.
	private DynamicPricingConfig defaultConfig(ZoneServicePricing pricing) {
		return DynamicPricingConfig.builder()
				.zoneServicePricing(pricing)
				.enabled(false)
				.demandThresholdLow(new BigDecimal("0.50"))
				.demandThresholdHigh(new BigDecimal("1.50"))
				.minMultiplier(BigDecimal.ONE)
				.maxMultiplier(new BigDecimal("2.00"))
				.recomputeIntervalMins(10)
				.build();
	}

	private DynamicPricingConfigResponse toConfigResponse(Long pricingId, DynamicPricingConfig config) {
		return new DynamicPricingConfigResponse(pricingId, config.isEnabled(), config.getDemandThresholdLow(), config.getDemandThresholdHigh(),
				config.getMinMultiplier(), config.getMaxMultiplier(), config.getRecomputeIntervalMins());
	}

}
