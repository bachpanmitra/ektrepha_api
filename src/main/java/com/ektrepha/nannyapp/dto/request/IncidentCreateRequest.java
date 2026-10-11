package com.ektrepha.nannyapp.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record IncidentCreateRequest(
		String shiftId,
		@NotBlank @Pattern(regexp = "OUTSIDE_CHILDCARE_WORK|FELT_UNSAFE|RUDE_BEHAVIOUR|PAYMENT_PROBLEM|OTHER") String category,
		@NotBlank @Size(max = 2000) String description, boolean requestCallback) {
}
