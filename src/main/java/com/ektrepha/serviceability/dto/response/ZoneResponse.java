package com.ektrepha.serviceability.dto.response;

public record ZoneResponse(
		Long id,
		String name,
		String city,
		String state,
		Double centroidLat,
		Double centroidLng,
		boolean active) {
}
