package com.ektrepha.serviceability.impl;

import java.util.List;
import java.util.Optional;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import com.ektrepha.config.CacheConfig;
import com.ektrepha.model.ServiceabilityPincode;
import com.ektrepha.repository.ServiceabilityPincodeRepository;
import com.ektrepha.serviceability.service.ServiceabilityLookupStrategy;

import lombok.RequiredArgsConstructor;

/** Exact-match lookup — by far the highest-traffic, highest-cache-hit-rate path (design target ~1M/day), since the same handful of pincodes repeat across most users. */
@Component
@RequiredArgsConstructor
public class PincodeLookupStrategy implements ServiceabilityLookupStrategy {

	private final ServiceabilityPincodeRepository pincodeRepository;

	@Override
	public String matchType() {
		return "PINCODE";
	}

	@Override
	public boolean supports(SearchCriteria criteria) {
		return criteria.pincode() != null && !criteria.pincode().isBlank();
	}

	@Override
	@Cacheable(cacheNames = CacheConfig.ZONE_BY_PINCODE, key = "#criteria.pincode()", unless = "#result.isEmpty()")
	public List<ZoneMatch> resolve(SearchCriteria criteria) {
		Optional<ServiceabilityPincode> mapping = pincodeRepository.findByPincode(criteria.pincode());
		if (mapping.isEmpty() || !mapping.get().isServiceable()) {
			return List.of();
		}
		ServiceabilityPincode pincode = mapping.get();
		return List.of(new ZoneMatch(pincode.getZoneArea(), pincode.getPincode(), null));
	}

}
