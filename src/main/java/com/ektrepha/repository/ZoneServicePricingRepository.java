package com.ektrepha.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.ektrepha.config.CacheConfig;
import com.ektrepha.model.ZoneServicePricing;

public interface ZoneServicePricingRepository extends JpaRepository<ZoneServicePricing, Long> {

	Optional<ZoneServicePricing> findByZoneAreaIdAndServiceTypeId(Long zoneAreaId, Long serviceTypeId);

	// Read on every /pricing/calculate and /serviceability/search call — cached (Proxy pattern via
	// Spring's caching AOP) since this row only changes on an admin edit, which evicts by the same
	// key (see ZonePricingServiceImpl).
	// Spring's caching support unwraps an Optional<T> return type for caching purposes, so #result
	// here is a plain ZoneServicePricing (or null) - not the Optional wrapper.
	@Cacheable(cacheNames = CacheConfig.PRICING_BY_ZONE_SERVICE, key = "#zoneAreaId + ':' + #serviceTypeId", unless = "#result == null")
	Optional<ZoneServicePricing> findByZoneAreaIdAndServiceTypeIdAndActiveTrue(Long zoneAreaId, Long serviceTypeId);

	List<ZoneServicePricing> findAllByZoneAreaId(Long zoneAreaId);

	boolean existsByZoneAreaIdAndServiceTypeId(Long zoneAreaId, Long serviceTypeId);

}
