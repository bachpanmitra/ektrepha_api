package com.ektrepha.serviceability.service;

import java.util.List;

import com.ektrepha.serviceability.dto.response.LiveZoneResponse;
import com.ektrepha.serviceability.dto.response.LocalityOptionResponse;
import com.ektrepha.serviceability.dto.response.ServiceabilityMatrixResponse;

public interface ServiceabilitySearchService {

	/** Exactly one of the parameters (or the lat/lng pair) is expected to be non-null; see {@code ServiceabilityLookupStrategyFactory}. */
	ServiceabilityMatrixResponse search(String pincode, String query, Double lat, Double lng, String city, String state);

	/** Every active zone with at least one LIVE service type, for public "where we're live" marketing use. */
	List<LiveZoneResponse> listLiveZones();

	/** Typeahead area search for a "select your area" search box - a blank query returns no results rather than erroring. */
	List<LocalityOptionResponse> searchLocalities(String query, int limit);

}
