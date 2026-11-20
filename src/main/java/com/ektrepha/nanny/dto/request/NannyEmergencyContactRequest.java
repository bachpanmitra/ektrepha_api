package com.ektrepha.nanny.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record NannyEmergencyContactRequest(
		@NotBlank @Size(max = 100) String name,
		@NotBlank @Size(max = 50) String relation,
		@NotBlank @Size(max = 15) String phone) {
}
