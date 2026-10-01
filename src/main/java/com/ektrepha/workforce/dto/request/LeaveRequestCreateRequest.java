package com.ektrepha.workforce.dto.request;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record LeaveRequestCreateRequest(
		@NotNull LocalDate startDate,
		@NotNull LocalDate endDate,
		@NotBlank String reason) {
}
