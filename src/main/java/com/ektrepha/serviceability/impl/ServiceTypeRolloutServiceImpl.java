package com.ektrepha.serviceability.impl;

import java.time.Instant;
import java.util.Optional;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ektrepha.config.CacheConfig;
import com.ektrepha.exception.ServiceTypeNotFoundException;
import com.ektrepha.exception.ZoneNotFoundException;
import com.ektrepha.model.ServiceType;
import com.ektrepha.model.ServiceabilityServiceType;
import com.ektrepha.model.ServiceabilityStatus;
import com.ektrepha.model.ZoneArea;
import com.ektrepha.repository.ServiceTypeRepository;
import com.ektrepha.repository.ServiceabilityServiceTypeRepository;
import com.ektrepha.repository.ZoneAreaRepository;
import com.ektrepha.serviceability.dto.request.ServiceTypeRolloutRequest;
import com.ektrepha.serviceability.dto.response.ServiceTypeRolloutResponse;
import com.ektrepha.serviceability.event.ServiceTypeStatusChangedEvent;
import com.ektrepha.serviceability.service.ServiceTypeRolloutService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ServiceTypeRolloutServiceImpl implements ServiceTypeRolloutService {

	private final ServiceabilityServiceTypeRepository rolloutRepository;
	private final ZoneAreaRepository zoneAreaRepository;
	private final ServiceTypeRepository serviceTypeRepository;
	private final ApplicationEventPublisher eventPublisher;
	private final CacheManager cacheManager;

	@Override
	@Transactional
	public ServiceTypeRolloutResponse setStatus(Long zoneId, Long serviceTypeId, ServiceTypeRolloutRequest request) {
		ZoneArea zone = zoneAreaRepository.findById(zoneId)
				.orElseThrow(() -> new ZoneNotFoundException("No zone with id " + zoneId));
		ServiceType serviceType = serviceTypeRepository.findById(serviceTypeId)
				.orElseThrow(() -> new ServiceTypeNotFoundException("No service type with id " + serviceTypeId));

		ServiceabilityServiceType rollout = rolloutRepository.findByZoneAreaIdAndServiceTypeId(zoneId, serviceTypeId)
				.orElseGet(() -> ServiceabilityServiceType.builder()
						.zoneArea(zone)
						.serviceType(serviceType)
						.status(ServiceabilityStatus.NOT_PLANNED)
						.build());

		ServiceabilityStatus previousStatus = rollout.getStatus();
		rollout.setStatus(request.status());
		if (request.status() == ServiceabilityStatus.LIVE && previousStatus != ServiceabilityStatus.LIVE) {
			rollout.setLaunchedAt(Instant.now());
		}
		ServiceabilityServiceType saved = rolloutRepository.save(rollout);

		evictServiceTypeStatusCache(zoneId);
		if (previousStatus != saved.getStatus()) {
			eventPublisher.publishEvent(new ServiceTypeStatusChangedEvent(zoneId, serviceTypeId, serviceType.getCode(), previousStatus, saved.getStatus()));
		}

		return new ServiceTypeRolloutResponse(zoneId, serviceTypeId, serviceType.getCode(), saved.getStatus(), saved.getLaunchedAt());
	}

	private void evictServiceTypeStatusCache(Long zoneAreaId) {
		Cache cache = cacheManager.getCache(CacheConfig.SERVICE_TYPE_STATUS);
		if (cache != null) {
			cache.evict(zoneAreaId);
		}
	}

}
