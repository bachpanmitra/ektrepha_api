package com.ektrepha.caremanager.dto.request;

import com.ektrepha.model.ComplaintCategory;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** docs/api/openapi.yaml#/components/schemas/RaiseComplaintRequest */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record RaiseComplaintRequest(
		@NotBlank String parentId,
		String nannyId,
		String bookingId,
		@NotNull ComplaintCategory category,
		@NotBlank @Size(min = 5, max = 2000) String description) {
}
