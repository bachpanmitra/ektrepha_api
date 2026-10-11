package com.ektrepha.nannyapp.dto.request;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record LeaveCreateRequest(
		@NotBlank @Pattern(regexp = "CASUAL|SICK|EARNED") String type, @NotNull LocalDate fromDate,
		@NotNull LocalDate toDate) {
}
