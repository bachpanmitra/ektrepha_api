package com.ektrepha.parent.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** {@code lastName} is intentionally optional — single-name users are common in India (migration 023). */
public record ParentProfileUpdateRequest(
		@NotBlank @Size(max = 100) String firstName,
		@Size(max = 100) String lastName) {
}
