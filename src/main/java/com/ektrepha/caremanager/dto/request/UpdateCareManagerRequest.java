package com.ektrepha.caremanager.dto.request;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

/** docs/api/openapi.yaml#/components/schemas/UpdateCareManagerRequest — every field optional, any subset. */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record UpdateCareManagerRequest(
		String name,
		@Pattern(regexp = "^\\+91\\d{10}$") String phone,
		@Email String email,
		@Min(1) @Max(500) Integer maxParents,
		@Pattern(regexp = "^\\d{2}:\\d{2}$") String shiftStart,
		@Pattern(regexp = "^\\d{2}:\\d{2}$") String shiftEnd) {
}
