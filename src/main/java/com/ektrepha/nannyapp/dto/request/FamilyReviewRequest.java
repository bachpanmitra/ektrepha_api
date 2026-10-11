package com.ektrepha.nannyapp.dto.request;

import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record FamilyReviewRequest(
		@Min(1) @Max(5) int stars, List<String> tags, @Size(max = 1000) String privateNote) {
}
