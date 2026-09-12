package com.ektrepha.serviceability.impl;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.ektrepha.config.properties.AppProperties;
import com.ektrepha.model.ZoneArea;
import com.ektrepha.repository.ZoneAreaRepository;
import com.ektrepha.serviceability.service.ServiceabilityLookupStrategy;

import lombok.RequiredArgsConstructor;

/** Nearest-zone-by-centroid via earthdistance. A match further than {@code app.serviceability.max-zone-match-km} is treated as no match, not "whatever zone happened to be closest". */
@Component
@RequiredArgsConstructor
public class CoordinateLookupStrategy implements ServiceabilityLookupStrategy {

	private static final double EARTH_RADIUS_KM = 6371.0;

	private final ZoneAreaRepository zoneAreaRepository;
	private final AppProperties appProperties;

	@Override
	public String matchType() {
		return "COORDINATES";
	}

	@Override
	public boolean supports(SearchCriteria criteria) {
		return criteria.lat() != null && criteria.lng() != null;
	}

	@Override
	public List<ZoneMatch> resolve(SearchCriteria criteria) {
		Optional<ZoneArea> nearest = zoneAreaRepository.findNearestByCoordinates(criteria.lat(), criteria.lng());
		if (nearest.isEmpty()) {
			return List.of();
		}
		ZoneArea zone = nearest.get();
		double distanceKm = haversineKm(criteria.lat(), criteria.lng(), zone.getCentroidLat(), zone.getCentroidLng());
		if (distanceKm > appProperties.serviceability().maxZoneMatchKm()) {
			return List.of();
		}
		return List.of(new ZoneMatch(zone, null, distanceKm));
	}

	private double haversineKm(double lat1, double lng1, double lat2, double lng2) {
		double dLat = Math.toRadians(lat2 - lat1);
		double dLng = Math.toRadians(lng2 - lng1);
		double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
				+ Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
						* Math.sin(dLng / 2) * Math.sin(dLng / 2);
		double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
		return EARTH_RADIUS_KM * c;
	}

}
