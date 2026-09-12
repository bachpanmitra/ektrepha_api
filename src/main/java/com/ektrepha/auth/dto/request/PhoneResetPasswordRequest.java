package com.ektrepha.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** firebaseIdToken proves phone ownership (Firebase Phone Auth) — no separate OTP step needed. */
public record PhoneResetPasswordRequest(
		@NotBlank String firebaseIdToken,
		@NotBlank @Size(min = 8, max = 100) String newPassword) {
}
