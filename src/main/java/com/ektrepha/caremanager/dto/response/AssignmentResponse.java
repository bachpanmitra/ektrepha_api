package com.ektrepha.caremanager.dto.response;

import java.time.Instant;

import com.ektrepha.model.CmAssignmentReason;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/** docs/api/openapi.yaml#/components/schemas/Assignment */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record AssignmentResponse(
		PersonRefResponse careManager,
		Instant assignedAt,
		Instant unassignedAt,
		PersonRefResponse assignedBy,
		CmAssignmentReason reason) {
}
