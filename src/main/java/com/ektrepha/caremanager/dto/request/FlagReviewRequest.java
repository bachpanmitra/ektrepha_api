package com.ektrepha.caremanager.dto.request;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** docs/api/openapi.yaml#/components/schemas/FlagReviewRequest */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record FlagReviewRequest(@NotNull Decision decision, @NotBlank @Size(min = 5, max = 1000) String reason) {

	public enum Decision {
		CLEAR, SUSPEND
	}

}
