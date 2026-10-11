package com.ektrepha.nannyapp.dto.request;

/** Check-in/check-out body. lat/lng are geofenced against the booking address; code is accepted but not yet verified (the parent app does not show one). */
public record ShiftLocationRequest(
		String code, Double lat, Double lng) {
}
