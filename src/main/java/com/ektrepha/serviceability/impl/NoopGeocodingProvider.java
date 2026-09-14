package com.ektrepha.serviceability.impl;

import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import com.ektrepha.serviceability.service.GeocodingProvider;

import lombok.extern.slf4j.Slf4j;

/**
 * Fallback {@link GeocodingProvider} bean, only registered when no real adapter is present (see
 * {@link NominatimGeocodingProvider}) — free-text search then always misses and the caller falls
 * back to "not serviceable" rather than erroring. This is the design-doc question #3 substitution
 * point, done as an Adapter rather than a hardcoded HTTP call in the search strategy.
 */
@Slf4j
@Component
@ConditionalOnMissingBean(GeocodingProvider.class)
public class NoopGeocodingProvider implements GeocodingProvider {

	@Override
	public String name() {
		return "noop";
	}

	@Override
	public Optional<LatLng> geocode(String freeTextQuery) {
		log.debug("Geocoding provider not configured — free-text query '{}' cannot be resolved", freeTextQuery);
		return Optional.empty();
	}

}
