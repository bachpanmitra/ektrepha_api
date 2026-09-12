package com.ektrepha.auth.dto.request;

import com.ektrepha.model.UserType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** firebaseIdToken proves phone ownership (Firebase Phone Auth) — no separate OTP step needed. */
public record PhoneSignupRequest(
		@NotBlank String firebaseIdToken,
		@NotBlank @Size(min = 8, max = 100) String password,
		@NotNull UserType role) {
}
