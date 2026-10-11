package com.ektrepha.nannyapp.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record ExtensionRequest(
		@Min(15) @Max(240) int minutes) {
}
