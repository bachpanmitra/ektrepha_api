package com.ektrepha.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.ektrepha.config.CacheConfig;
import com.ektrepha.model.ServiceabilityServiceType;

public interface ServiceabilityServiceTypeRepository extends JpaRepository<ServiceabilityServiceType, Long> {

	// Backs the serviceability search matrix on every request; evicted by zone id in
	// ServiceTypeRolloutServiceImpl whenever a rollout status changes.
	@Cacheable(cacheNames = CacheConfig.SERVICE_TYPE_STATUS, key = "#zoneAreaId")
	List<ServiceabilityServiceType> findAllByZoneAreaId(Long zoneAreaId);

	Optional<ServiceabilityServiceType> findByZoneAreaIdAndServiceTypeId(Long zoneAreaId, Long serviceTypeId);

}
