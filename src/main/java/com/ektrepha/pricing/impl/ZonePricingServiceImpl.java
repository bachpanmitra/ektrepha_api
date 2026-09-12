package com.ektrepha.pricing.impl;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ektrepha.config.CacheConfig;
import com.ektrepha.exception.DuplicateZonePricingException;
import com.ektrepha.exception.ServiceTypeNotFoundException;
import com.ektrepha.exception.ZoneNotFoundException;
import com.ektrepha.exception.ZonePricingNotFoundException;
import com.ektrepha.model.PricingMode;
import com.ektrepha.model.ServiceType;
import com.ektrepha.model.ZoneArea;
import com.ektrepha.model.ZoneServicePricing;
import com.ektrepha.pricing.dto.request.ZonePricingCreateRequest;
import com.ektrepha.pricing.dto.request.ZonePricingUpdateRequest;
import com.ektrepha.pricing.dto.response.ZonePricingResponse;
import com.ektrepha.pricing.service.ZonePricingService;
import com.ektrepha.repository.ServiceTypeRepository;
import com.ektrepha.repository.ZoneAreaRepository;
import com.ektrepha.repository.ZoneServicePricingRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ZonePricingServiceImpl implements ZonePricingService {

	private final ZoneServicePricingRepository pricingRepository;
	private final ZoneAreaRepository zoneAreaRepository;
	private final ServiceTypeRepository serviceTypeRepository;
	private final CacheManager cacheManager;

	@Override
	@Transactional(readOnly = true)
	public List<ZonePricingResponse> list(Long zoneId) {
		return pricingRepository.findAllByZoneAreaId(zoneId).stream().map(this::toResponse).toList();
	}

	@Override
	@Transactional
	public ZonePricingResponse create(Long zoneId, ZonePricingCreateRequest request) {
		ZoneArea zone = zoneAreaRepository.findById(zoneId)
				.orElseThrow(() -> new ZoneNotFoundException("No zone with id " + zoneId));
		ServiceType serviceType = serviceTypeRepository.findById(request.serviceTypeId())
				.orElseThrow(() -> new ServiceTypeNotFoundException("No service type with id " + request.serviceTypeId()));
		if (pricingRepository.existsByZoneAreaIdAndServiceTypeId(zoneId, serviceType.getId())) {
			throw new DuplicateZonePricingException("Pricing is already configured for zone " + zoneId + " and service type " + serviceType.getId());
		}
		validatePricingFields(request.pricingMode(), request.fixPrice(), request.rateMin(), request.rateMax());

		ZoneServicePricing pricing = ZoneServicePricing.builder()
				.zoneArea(zone)
				.serviceType(serviceType)
				.pricingMode(request.pricingMode())
				.fixPrice(request.fixPrice())
				.rateMin(request.rateMin())
				.rateMax(request.rateMax())
				.unitPrice(request.unitPrice())
				.currency(request.currency() != null ? request.currency() : "INR")
				.minBookingHours(request.minBookingHours() != null ? request.minBookingHours() : BigDecimal.ONE)
				.platformFeePct(request.platformFeePct() != null ? request.platformFeePct() : BigDecimal.ZERO)
				.active(true)
				.build();
		ZoneServicePricing saved = pricingRepository.save(pricing);
		evictPricingCache(zoneId, serviceType.getId());
		return toResponse(saved);
	}

	@Override
	@Transactional
	public ZonePricingResponse update(Long pricingId, ZonePricingUpdateRequest request) {
		ZoneServicePricing pricing = pricingRepository.findById(pricingId)
				.orElseThrow(() -> new ZonePricingNotFoundException("No pricing row with id " + pricingId));
		validatePricingFields(request.pricingMode(), request.fixPrice(), request.rateMin(), request.rateMax());

		pricing.setPricingMode(request.pricingMode());
		pricing.setFixPrice(request.fixPrice());
		pricing.setRateMin(request.rateMin());
		pricing.setRateMax(request.rateMax());
		pricing.setUnitPrice(request.unitPrice());
		if (request.currency() != null) {
			pricing.setCurrency(request.currency());
		}
		if (request.minBookingHours() != null) {
			pricing.setMinBookingHours(request.minBookingHours());
		}
		if (request.platformFeePct() != null) {
			pricing.setPlatformFeePct(request.platformFeePct());
		}
		ZoneServicePricing saved = pricingRepository.save(pricing);
		evictPricingCache(saved.getZoneArea().getId(), saved.getServiceType().getId());
		return toResponse(saved);
	}

	// Backstops the DB CHECK constraints (migration 008) with a clean 400 instead of a raw
	// constraint-violation error bubbling up from the flush.
	private void validatePricingFields(PricingMode mode, BigDecimal fixPrice, BigDecimal rateMin, BigDecimal rateMax) {
		if (mode == PricingMode.FIXED && fixPrice == null) {
			throw new IllegalArgumentException("fixPrice is required when pricingMode is FIXED");
		}
		if (mode == PricingMode.RANGE && (rateMin == null || rateMax == null || rateMin.compareTo(rateMax) > 0)) {
			throw new IllegalArgumentException("rateMin and rateMax are required when pricingMode is RANGE, with rateMin <= rateMax");
		}
	}

	private void evictPricingCache(Long zoneAreaId, Long serviceTypeId) {
		Cache cache = cacheManager.getCache(CacheConfig.PRICING_BY_ZONE_SERVICE);
		if (cache != null) {
			cache.evict(zoneAreaId + ":" + serviceTypeId);
		}
	}

	private ZonePricingResponse toResponse(ZoneServicePricing pricing) {
		return new ZonePricingResponse(
				pricing.getId(), pricing.getZoneArea().getId(), pricing.getServiceType().getId(), pricing.getServiceType().getCode(),
				pricing.getPricingMode(), pricing.getFixPrice(), pricing.getRateMin(), pricing.getRateMax(), pricing.getUnitPrice(),
				pricing.getCurrency(), pricing.getMinBookingHours(), pricing.getPlatformFeePct(), pricing.isActive());
	}

}
