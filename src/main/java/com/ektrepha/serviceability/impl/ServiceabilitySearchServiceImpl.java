package com.ektrepha.serviceability.impl;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ektrepha.model.ServiceType;
import com.ektrepha.model.ServiceabilityServiceType;
import com.ektrepha.model.ServiceabilityStatus;
import com.ektrepha.model.ZoneArea;
import com.ektrepha.model.ZoneServicePricing;
import com.ektrepha.repository.ServiceTypeRepository;
import com.ektrepha.repository.ServiceabilityServiceTypeRepository;
import com.ektrepha.repository.ZoneServicePricingRepository;
import com.ektrepha.repository.ZoneAreaRepository;
import com.ektrepha.serviceability.dto.response.LiveZoneResponse;
import com.ektrepha.serviceability.dto.response.LocalityOptionResponse;
import com.ektrepha.serviceability.dto.response.ServiceTypeAvailability;
import com.ektrepha.serviceability.dto.response.ServiceabilityMatrixResponse;
import com.ektrepha.serviceability.dto.response.ZoneMatrixEntry;
import com.ektrepha.serviceability.service.ServiceabilityLookupStrategy;
import com.ektrepha.serviceability.service.ServiceabilityLookupStrategy.SearchCriteria;
import com.ektrepha.serviceability.service.ServiceabilityLookupStrategy.ZoneMatch;
import com.ektrepha.serviceability.service.ServiceabilitySearchService;

import lombok.RequiredArgsConstructor;

/**
 * Facade: the public search endpoint's one job is "given whatever the caller sent, tell me
 * everything serviceable there" — that means combining a lookup strategy result with the
 * service-type rollout matrix and current pricing for each matched zone. Callers see one method;
 * the three underlying lookups (zone resolution, rollout status, pricing) stay in their own
 * repositories.
 */
@Service
@RequiredArgsConstructor
public class ServiceabilitySearchServiceImpl implements ServiceabilitySearchService {

	private final ServiceabilityLookupStrategyFactory strategyFactory;
	private final ServiceTypeRepository serviceTypeRepository;
	private final ServiceabilityServiceTypeRepository rolloutRepository;
	private final ZoneServicePricingRepository pricingRepository;
	private final ZoneAreaRepository zoneAreaRepository;

	@Override
	@Transactional(readOnly = true)
	public ServiceabilityMatrixResponse search(String pincode, String query, Double lat, Double lng, String city, String state) {
		SearchCriteria criteria = new SearchCriteria(pincode, query, lat, lng, city, state);
		ServiceabilityLookupStrategy strategy = strategyFactory.resolve(criteria);
		List<ZoneMatch> matches = strategy.resolve(criteria);

		List<ServiceType> activeServiceTypes = serviceTypeRepository.findAllByActiveTrueOrderByNameAsc();
		List<ZoneMatrixEntry> entries = matches.stream()
				.map(match -> toEntry(match, activeServiceTypes))
				.toList();

		return new ServiceabilityMatrixResponse(strategy.matchType(), entries);
	}

	@Override
	@Transactional(readOnly = true)
	public List<LiveZoneResponse> listLiveZones() {
		return zoneAreaRepository.findAllWithLiveService().stream()
				.map(zone -> {
					List<String> liveCodes = rolloutRepository.findAllByZoneAreaId(zone.getId()).stream()
							.filter(row -> row.getStatus() == ServiceabilityStatus.LIVE)
							.map(row -> row.getServiceType().getCode())
							.sorted()
							.toList();
					return new LiveZoneResponse(zone.getId(), zone.getName(), zone.getCity(), zone.getState(), liveCodes);
				})
				.toList();
	}

	@Override
	@Transactional(readOnly = true)
	public List<LocalityOptionResponse> searchLocalities(String query, int limit) {
		if (query == null || query.isBlank()) {
			return List.of();
		}
		int boundedLimit = Math.max(1, Math.min(limit, 25));
		List<ZoneArea> matches = zoneAreaRepository.searchByNameFuzzy(query.trim(), PageRequest.of(0, boundedLimit));
		return matches.stream()
				.map(zone -> new LocalityOptionResponse(zone.getId(), zone.getName(), zone.getCity(), zone.getState(), hasLiveService(zone)))
				.toList();
	}

	private boolean hasLiveService(ZoneArea zone) {
		return rolloutRepository.findAllByZoneAreaId(zone.getId()).stream()
				.anyMatch(row -> row.getStatus() == ServiceabilityStatus.LIVE);
	}

	private ZoneMatrixEntry toEntry(ZoneMatch match, List<ServiceType> activeServiceTypes) {
		ZoneArea zone = match.zoneArea();
		Map<Long, ServiceabilityStatus> statusByServiceTypeId = rolloutRepository.findAllByZoneAreaId(zone.getId()).stream()
				.collect(java.util.stream.Collectors.toMap(row -> row.getServiceType().getId(), ServiceabilityServiceType::getStatus));

		List<ServiceTypeAvailability> availabilities = activeServiceTypes.stream()
				.map(serviceType -> toAvailability(zone, serviceType, statusByServiceTypeId))
				.toList();

		return new ZoneMatrixEntry(zone.getId(), zone.getName(), zone.getCity(), zone.getState(),
				match.pincode(), match.distanceKm(), availabilities);
	}

	private ServiceTypeAvailability toAvailability(ZoneArea zone, ServiceType serviceType, Map<Long, ServiceabilityStatus> statusByServiceTypeId) {
		ServiceabilityStatus status = statusByServiceTypeId.getOrDefault(serviceType.getId(), ServiceabilityStatus.NOT_PLANNED);
		Optional<ZoneServicePricing> pricing = status == ServiceabilityStatus.LIVE
				? pricingRepository.findByZoneAreaIdAndServiceTypeIdAndActiveTrue(zone.getId(), serviceType.getId())
				: Optional.empty();

		return new ServiceTypeAvailability(
				serviceType.getCode(),
				serviceType.getName(),
				status,
				serviceType.getPricingUnit(),
				pricing.map(ZoneServicePricing::getPricingMode).orElse(null),
				pricing.map(ZoneServicePricing::getFixPrice).orElse(null),
				pricing.map(ZoneServicePricing::getRateMin).orElse(null),
				pricing.map(ZoneServicePricing::getRateMax).orElse(null),
				pricing.map(ZoneServicePricing::getCurrency).orElse(null));
	}

}
