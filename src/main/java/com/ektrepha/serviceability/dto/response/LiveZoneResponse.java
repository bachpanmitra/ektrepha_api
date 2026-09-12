package com.ektrepha.serviceability.dto.response;

import java.util.List;

/** One row of the public "where we're live" marketing list — a zone with at least one LIVE service type. */
public record LiveZoneResponse(
		Long zoneAreaId,
		String zoneName,
		String city,
		String state,
		List<String> liveServiceTypeCodes) {
}
