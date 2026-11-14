package com.ektrepha.caremanager.dto.request;

import com.ektrepha.model.CmArea;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** docs/api/openapi.yaml#/components/schemas/CreateCareManagerRequest */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CreateCareManagerRequest(
		@NotBlank @Size(min = 2, max = 100) String name,
		@NotBlank @Pattern(regexp = "^\\+91\\d{10}$") String phone,
		@NotBlank @Email String email,
		@NotNull CmArea area,
		@Min(1) @Max(500) Integer maxParents,
		@NotBlank @Pattern(regexp = "^\\d{2}:\\d{2}$") String shiftStart,
		@NotBlank @Pattern(regexp = "^\\d{2}:\\d{2}$") String shiftEnd) {

	public int maxParentsOrDefault() {
		return maxParents != null ? maxParents : 150;
	}

}
