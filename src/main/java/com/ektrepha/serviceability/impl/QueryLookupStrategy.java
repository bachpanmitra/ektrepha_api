package com.ektrepha.serviceability.impl;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.ektrepha.model.GeocodeCache;
import com.ektrepha.model.ZoneArea;
import com.ektrepha.repository.GeocodeCacheRepository;
import com.ektrepha.repository.ZoneAreaRepository;
import com.ektrepha.serviceability.service.GeocodingProvider;
import com.ektrepha.serviceability.service.ServiceabilityLookupStrategy;

import lombok.RequiredArgsConstructor;

/** Free-text address search: cache-aside on {@code geocode_cache} in front of the {@link GeocodingProvider} Adapter, then delegates to {@link CoordinateLookupStrategy} once resolved to a lat/lng. */
@Component
@RequiredArgsConstructor
public class QueryLookupStrategy implements ServiceabilityLookupStrategy {

	private final GeocodeCacheRepository geocodeCacheRepository;
	private final GeocodingProvider geocodingProvider;
	private final CoordinateLookupStrategy coordinateLookupStrategy;

	@Override
	public String matchType() {
		return "QUERY";
	}

	@Override
	public boolean supports(SearchCriteria criteria) {
		return criteria.query() != null && !criteria.query().isBlank();
	}

	@Override
	public List<ZoneMatch> resolve(SearchCriteria criteria) {
		String normalized = criteria.query().trim().toLowerCase(Locale.ROOT);
		Optional<GeocodeCache> cached = geocodeCacheRepository.findByNormalizedQuery(normalized);
		Double lat = cached.map(GeocodeCache::getLat).orElse(null);
		Double lng = cached.map(GeocodeCache::getLng).orElse(null);

		if (lat == null || lng == null) {
			Optional<GeocodingProvider.LatLng> geocoded = geocodingProvider.geocode(criteria.query());
			if (geocoded.isEmpty()) {
				return List.of();
			}
			lat = geocoded.get().lat();
			lng = geocoded.get().lng();
			geocodeCacheRepository.save(GeocodeCache.builder()
					.queryText(criteria.query())
					.normalizedQuery(normalized)
					.lat(lat)
					.lng(lng)
					.formattedAddress(geocoded.get().formattedAddress())
					.provider(geocodingProvider.name())
					.createdAt(Instant.now())
					.build());
		}

		return coordinateLookupStrategy.resolve(new SearchCriteria(null, null, lat, lng, null, null)).stream()
				.map(match -> new ZoneMatch(match.zoneArea(), match.pincode(), match.distanceKm()))
				.toList();
	}

}
