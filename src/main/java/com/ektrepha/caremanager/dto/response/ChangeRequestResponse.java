package com.ektrepha.caremanager.dto.response;

import java.time.Instant;

import com.ektrepha.model.CmTaskStatus;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/** docs/api/openapi.yaml#/components/schemas/ChangeRequest */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ChangeRequestResponse(
		String id,
		PersonRefResponse parent,
		String bookingId,
		String reason,
		CmTaskStatus status,
		Instant createdAt) {
}
