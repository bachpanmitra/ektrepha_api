package com.ektrepha.auth.dto.request;

import jakarta.validation.constraints.NotBlank;

public record PhoneLoginRequest(
		@NotBlank String phoneNumber,
		@NotBlank String password) {
}
