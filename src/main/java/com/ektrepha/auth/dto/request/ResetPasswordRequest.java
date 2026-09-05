package com.ektrepha.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
		@NotBlank String phoneOrEmail,
		@NotBlank String otp,
		@NotBlank @Size(min = 8, max = 100) String newPassword) {
}
