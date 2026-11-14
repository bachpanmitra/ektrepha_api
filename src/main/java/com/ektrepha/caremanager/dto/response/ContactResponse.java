package com.ektrepha.caremanager.dto.response;

import java.time.Instant;

import com.ektrepha.model.CmContactChannel;
import com.ektrepha.model.CmContactDirection;
import com.ektrepha.model.CmContactSource;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/** docs/api/openapi.yaml#/components/schemas/Contact */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ContactResponse(
		String id,
		CmContactChannel channel,
		CmContactDirection direction,
		CmContactSource source,
		String summary,
		String taskId,
		PersonRefResponse by,
		Instant contactedAt) {
}
