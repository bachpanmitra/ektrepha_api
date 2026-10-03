package com.ektrepha.verification.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record NannyReferenceSubmitRequest(
		@NotBlank @Size(max = 150) String name,
		@NotBlank @Size(max = 20) String phone,
		@Size(max = 100) String relationship) {
}
