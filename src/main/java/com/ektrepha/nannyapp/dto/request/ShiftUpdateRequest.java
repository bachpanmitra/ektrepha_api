package com.ektrepha.nannyapp.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ShiftUpdateRequest(
		@NotBlank @Size(max = 1000) String text) {
}
