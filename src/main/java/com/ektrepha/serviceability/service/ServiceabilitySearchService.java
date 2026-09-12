package com.ektrepha.serviceability.service;

import com.ektrepha.serviceability.dto.response.ServiceabilityMatrixResponse;

public interface ServiceabilitySearchService {

	/** Exactly one of the parameters (or the lat/lng pair) is expected to be non-null; see {@code ServiceabilityLookupStrategyFactory}. */
	ServiceabilityMatrixResponse search(String pincode, String query, Double lat, Double lng, String city, String state);

}
