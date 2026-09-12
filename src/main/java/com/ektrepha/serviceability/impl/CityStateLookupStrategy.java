package com.ektrepha.serviceability.impl;

import java.util.List;

import org.springframework.stereotype.Component;

import com.ektrepha.model.ZoneArea;
import com.ektrepha.repository.ZoneAreaRepository;
import com.ektrepha.serviceability.service.ServiceabilityLookupStrategy;

import lombok.RequiredArgsConstructor;

/** City+state can legitimately match more than one zone (e.g. multiple sub-zones of one city) — unlike the other strategies, this one may return several {@link ZoneMatch}es. */
@Component
@RequiredArgsConstructor
public class CityStateLookupStrategy implements ServiceabilityLookupStrategy {

	private final ZoneAreaRepository zoneAreaRepository;

	@Override
	public String matchType() {
		return "CITY_STATE";
	}

	@Override
	public boolean supports(SearchCriteria criteria) {
		return criteria.city() != null && !criteria.city().isBlank()
				&& criteria.state() != null && !criteria.state().isBlank();
	}

	@Override
	public List<ZoneMatch> resolve(SearchCriteria criteria) {
		List<ZoneArea> zones = zoneAreaRepository.findAllByCityIgnoreCaseAndStateIgnoreCaseAndActiveTrue(criteria.city(), criteria.state());
		return zones.stream().map(zone -> new ZoneMatch(zone, null, null)).toList();
	}

}
