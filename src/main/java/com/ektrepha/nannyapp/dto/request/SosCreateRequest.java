package com.ektrepha.nannyapp.dto.request;

/** Both optional - SOS must work without location permission. */
public record SosCreateRequest(
		Double lat, Double lng) {
}
