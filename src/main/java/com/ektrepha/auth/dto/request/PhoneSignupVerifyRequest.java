package com.ektrepha.auth.dto.request;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PhoneSignupVerifyRequest(
		@NotNull UUID signupSessionId,
		@NotBlank String otp) {
}
