package com.ektrepha.serviceability.dto.request;

import jakarta.validation.constraints.NotBlank;

public record ZoneCreateRequest(
		@NotBlank String name,
		@NotBlank String city,
		@NotBlank String state,
		Double centroidLat,
		Double centroidLng) {
}
