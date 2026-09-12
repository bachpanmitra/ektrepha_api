package com.ektrepha.serviceability.service;

import java.util.Optional;

/**
 * Adapter pattern: unifies whichever third-party geocoder is behind free-text address search
 * (Google, MapMyIndia, ...) behind one method. {@code QueryLookupStrategy} depends only on this
 * interface and on {@code geocode_cache} — swapping the vendor (or adding a real one; see
 * {@code NoopGeocodingProvider}) never touches the search flow.
 */
public interface GeocodingProvider {

	/** This provider's {@code geocode_cache.provider} tag. */
	String name();

	Optional<LatLng> geocode(String freeTextQuery);

	record LatLng(double lat, double lng, String formattedAddress) {
	}

}
