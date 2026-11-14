package com.ektrepha.caremanager.dto.response;

import java.time.Instant;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/** docs/api/openapi.yaml#/components/schemas/FamilyNotes (allOf FamilyNotesInput) */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record FamilyNotesResponse(
		String childRoutine,
		String allergies,
		String preferences,
		String expectations,
		PersonRefResponse updatedBy,
		Instant updatedAt,
		String version) {
}
