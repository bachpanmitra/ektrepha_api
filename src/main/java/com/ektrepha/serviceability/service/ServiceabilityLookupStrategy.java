package com.ektrepha.serviceability.service;

import java.util.List;

import com.ektrepha.model.ZoneArea;

/**
 * Strategy pattern: {@code GET /serviceability/search} accepts four mutually-exclusive lookup
 * modes (pincode, free-text query, coordinates, city+state). Each mode is one implementation of
 * this interface; {@code ServiceabilityLookupStrategyFactory} (Factory Method) picks the one whose
 * {@link #supports(SearchCriteria)} matches, so the search service never branches on which
 * parameters were supplied.
 */
public interface ServiceabilityLookupStrategy {

	/** A short tag identifying this strategy's match type in the API response (e.g. "PINCODE"). */
	String matchType();

	boolean supports(SearchCriteria criteria);

	/** Resolves the request to zero or more candidate zones. Empty means "not serviceable here", not an error. */
	List<ZoneMatch> resolve(SearchCriteria criteria);

	record SearchCriteria(String pincode, String query, Double lat, Double lng, String city, String state) {
	}

	record ZoneMatch(ZoneArea zoneArea, String pincode, Double distanceKm) {
	}

}
