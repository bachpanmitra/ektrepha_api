package com.ektrepha.serviceability.dto.response;

import java.util.List;

public record ZoneMatrixEntry(
		Long zoneAreaId,
		String zoneName,
		String city,
		String state,
		String pincode,
		Double distanceKm,
		List<ServiceTypeAvailability> serviceTypes) {
}
