package com.ektrepha.nannyapp.dto.response;

public record IncidentResponse(
		String id, String shiftId, String category, String description, boolean requestCallback) {
}
