package com.ektrepha.nannyapp.dto.request;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AttendanceCorrectionCreateRequest(
		@NotNull LocalDate date, @NotBlank @Size(max = 500) String note) {
}
