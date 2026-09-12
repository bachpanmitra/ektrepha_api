package com.ektrepha.serviceability.impl;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.ektrepha.serviceability.service.GeocodingProvider;

import lombok.extern.slf4j.Slf4j;

/**
 * Default {@link GeocodingProvider} bean — no vendor API key is configured yet, so free-text
 * search always misses and the caller falls back to "not serviceable" rather than erroring. Swap
 * in a real adapter (Google/MapMyIndia) as another {@code @Component GeocodingProvider} bean —
 * exactly the design-doc question #3 substitution point, done as an Adapter rather than a
 * hardcoded HTTP call in the search strategy.
 */
@Slf4j
@Component
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
