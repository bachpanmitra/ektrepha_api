package com.ektrepha.caremanager.dto.request;

import com.ektrepha.model.CmContactChannel;
import com.ektrepha.model.CmContactDirection;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** docs/api/openapi.yaml#/components/schemas/CmLogContactRequest */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CmLogContactRequest(
		@NotNull CmContactChannel channel,
		@NotNull CmContactDirection direction,
		@Size(max = 1000) String summary,
		String taskId) {
}
