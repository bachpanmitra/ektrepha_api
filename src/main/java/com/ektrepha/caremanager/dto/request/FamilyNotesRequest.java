package com.ektrepha.caremanager.dto.request;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

import jakarta.validation.constraints.Size;

/** docs/api/openapi.yaml#/components/schemas/FamilyNotesInput */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record FamilyNotesRequest(
		@Size(max = 2000) String childRoutine,
		@Size(max = 1000) String allergies,
		@Size(max = 2000) String preferences,
		@Size(max = 2000) String expectations) {
}
