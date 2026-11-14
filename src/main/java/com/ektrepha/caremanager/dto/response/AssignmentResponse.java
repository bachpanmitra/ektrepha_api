package com.ektrepha.caremanager.dto.response;

import java.time.Instant;

import com.ektrepha.model.CmAssignmentReason;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/** docs/api/openapi.yaml#/components/schemas/Assignment */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record AssignmentResponse(
		PersonRefResponse careManager,
		Instant assignedAt,
		Instant unassignedAt,
		PersonRefResponse assignedBy,
		CmAssignmentReason reason) {
}
