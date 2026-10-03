package com.ektrepha.pricing.impl;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

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
import com.ektrepha.model.ServiceabilityPincode;
import com.ektrepha.model.ZoneArea;
import com.ektrepha.model.ZoneServicePricing;
import com.ektrepha.pricing.dto.request.ZonePricingCreateRequest;
import com.ektrepha.pricing.dto.request.ZonePricingUpdateRequest;
import com.ektrepha.pricing.dto.request.ZoneServicePricingBulkItem;
import com.ektrepha.pricing.dto.response.ZonePricingResponse;
import com.ektrepha.pricing.dto.response.ZoneServicePricingRowResponse;
import com.ektrepha.pricing.service.ZonePricingService;
import com.ektrepha.repository.ServiceTypeRepository;
import com.ektrepha.repository.ServiceabilityPincodeRepository;
import com.ektrepha.repository.ZoneAreaRepository;
import com.ektrepha.repository.ZoneServicePricingRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ZonePricingServiceImpl implements ZonePricingService {

	private final ZoneServicePricingRepository pricingRepository;
	private final ZoneAreaRepository zoneAreaRepository;
	private final ServiceTypeRepository serviceTypeRepository;
	private final ServiceabilityPincodeRepository pincodeRepository;
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
		validatePricingFields(request.pricingMode(), request.fixPrice(), request.rateMin(), request.rateMax(), request.monthlyPrice());

		ZoneServicePricing pricing = ZoneServicePricing.builder()
				.zoneArea(zone)
				.serviceType(serviceType)
				.pricingMode(request.pricingMode())
				.fixPrice(request.fixPrice())
				.rateMin(request.rateMin())
				.rateMax(request.rateMax())
				.unitPrice(request.unitPrice())
				.monthlyPrice(request.monthlyPrice())
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
		validatePricingFields(request.pricingMode(), request.fixPrice(), request.rateMin(), request.rateMax(), request.monthlyPrice());

		pricing.setPricingMode(request.pricingMode());
		pricing.setFixPrice(request.fixPrice());
		pricing.setRateMin(request.rateMin());
		pricing.setRateMax(request.rateMax());
		pricing.setUnitPrice(request.unitPrice());
		pricing.setMonthlyPrice(request.monthlyPrice());
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

	@Override
	@Transactional(readOnly = true)
	public List<ZoneServicePricingRowResponse> listByServiceType(Long serviceTypeId) {
		if (!serviceTypeRepository.existsById(serviceTypeId)) {
			throw new ServiceTypeNotFoundException("No service type with id " + serviceTypeId);
		}
		return buildRows(serviceTypeId);
	}

	@Override
	@Transactional
	public List<ZoneServicePricingRowResponse> bulkUpsert(Long serviceTypeId, List<ZoneServicePricingBulkItem> items) {
		ServiceType serviceType = serviceTypeRepository.findById(serviceTypeId)
				.orElseThrow(() -> new ServiceTypeNotFoundException("No service type with id " + serviceTypeId));

		for (ZoneServicePricingBulkItem item : items) {
			ZoneArea zone = zoneAreaRepository.findById(item.zoneId())
					.orElseThrow(() -> new ZoneNotFoundException("No zone with id " + item.zoneId()));
			validatePricingFields(item.pricingMode(), item.fixPrice(), item.rateMin(), item.rateMax(), item.monthlyPrice());

			ZoneServicePricing pricing = pricingRepository.findByZoneAreaIdAndServiceTypeId(item.zoneId(), serviceTypeId)
					.orElseGet(() -> ZoneServicePricing.builder().zoneArea(zone).serviceType(serviceType).active(true).build());

			pricing.setPricingMode(item.pricingMode());
			pricing.setFixPrice(item.fixPrice());
			pricing.setRateMin(item.rateMin());
			pricing.setRateMax(item.rateMax());
			pricing.setUnitPrice(item.unitPrice());
			pricing.setMonthlyPrice(item.monthlyPrice());
			pricing.setCurrency(item.currency() != null ? item.currency() : "INR");
			pricing.setMinBookingHours(item.minBookingHours() != null ? item.minBookingHours() : BigDecimal.ONE);
			pricing.setPlatformFeePct(item.platformFeePct() != null ? item.platformFeePct() : BigDecimal.ZERO);
			pricingRepository.save(pricing);
			evictPricingCache(item.zoneId(), serviceTypeId);
		}

		return buildRows(serviceTypeId);
	}

	private List<ZoneServicePricingRowResponse> buildRows(Long serviceTypeId) {
		Map<Long, ZoneServicePricing> pricingByZoneId = pricingRepository.findAllByServiceTypeId(serviceTypeId).stream()
				.collect(Collectors.toMap(p -> p.getZoneArea().getId(), Function.identity()));
		Map<Long, List<String>> pincodesByZoneId = pincodeRepository.findAll().stream()
				.collect(Collectors.groupingBy(
						p -> p.getZoneArea().getId(),
						Collectors.mapping(ServiceabilityPincode::getPincode, Collectors.toList())));

		return zoneAreaRepository.findAllByOrderByCityAscNameAsc().stream()
				.map(zone -> toRowResponse(zone, pricingByZoneId.get(zone.getId()), pincodesByZoneId.getOrDefault(zone.getId(), List.of())))
				.toList();
	}

	private ZoneServicePricingRowResponse toRowResponse(ZoneArea zone, ZoneServicePricing pricing, List<String> pincodes) {
		List<String> sortedPincodes = pincodes.stream().sorted(Comparator.naturalOrder()).toList();
		if (pricing == null) {
			return new ZoneServicePricingRowResponse(
					zone.getId(), zone.getName(), zone.getCity(), zone.getState(), zone.isActive(), sortedPincodes,
					null, null, null, null, null, null, null, null, null, null, false);
		}
		return new ZoneServicePricingRowResponse(
				zone.getId(), zone.getName(), zone.getCity(), zone.getState(), zone.isActive(), sortedPincodes,
				pricing.getId(), pricing.getPricingMode(), pricing.getFixPrice(), pricing.getRateMin(), pricing.getRateMax(),
				pricing.getUnitPrice(), pricing.getMonthlyPrice(), pricing.getCurrency(), pricing.getMinBookingHours(),
				pricing.getPlatformFeePct(), pricing.isActive());
	}

	// Backstops the DB CHECK constraints (migration 008/041) with a clean 400 instead of a raw
	// constraint-violation error bubbling up from the flush.
	private void validatePricingFields(PricingMode mode, BigDecimal fixPrice, BigDecimal rateMin, BigDecimal rateMax, BigDecimal monthlyPrice) {
		if (mode == PricingMode.FIXED && fixPrice == null) {
			throw new IllegalArgumentException("fixPrice is required when pricingMode is FIXED");
		}
		if (mode == PricingMode.RANGE && (rateMin == null || rateMax == null || rateMin.compareTo(rateMax) > 0)) {
			throw new IllegalArgumentException("rateMin and rateMax are required when pricingMode is RANGE, with rateMin <= rateMax");
		}
		if (mode == PricingMode.MONTHLY && monthlyPrice == null) {
			throw new IllegalArgumentException("monthlyPrice is required when pricingMode is MONTHLY");
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
				pricing.getMonthlyPrice(), pricing.getCurrency(), pricing.getMinBookingHours(), pricing.getPlatformFeePct(), pricing.isActive());
	}

}
