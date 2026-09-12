package com.ektrepha.serviceability.dto.response;

import java.util.List;

/** Unified response for every {@code /serviceability/search} variant (pincode, free-text query, coordinates, city+state) — {@code matchType} tells the client which lookup strategy resolved the request. */
public record ServiceabilityMatrixResponse(
		String matchType,
		List<ZoneMatrixEntry> zones) {
}
