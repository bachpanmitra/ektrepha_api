package com.ektrepha.nannyapp.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EmergencyContactRequest(
		@NotBlank @Size(max = 100) String name, @NotBlank @Size(max = 50) String relation,
		@NotBlank @Size(max = 20) String phone) {
}
