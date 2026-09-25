package com.ektrepha.auth.dto.request;

import jakarta.validation.constraints.NotBlank;

public record MobileOtpVerifyRequest(
		@NotBlank String challengeId,
		@NotBlank String otp) {
}
