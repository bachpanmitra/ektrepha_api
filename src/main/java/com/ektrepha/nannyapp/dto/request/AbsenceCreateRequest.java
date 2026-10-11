package com.ektrepha.nannyapp.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record AbsenceCreateRequest(
		@NotBlank String shiftId,
		@NotBlank @Pattern(regexp = "UNWELL|FAMILY_EMERGENCY|RUNNING_LATE|TRAVEL_PROBLEM") String reason) {
}
