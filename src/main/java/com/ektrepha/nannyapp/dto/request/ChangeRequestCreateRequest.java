package com.ektrepha.nannyapp.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ChangeRequestCreateRequest(
		@NotBlank @Pattern(regexp = "SWAP|RESCHEDULE|LEAVE_FOR_SHIFT") String kind, @NotBlank @Size(max = 500) String reason) {
}
