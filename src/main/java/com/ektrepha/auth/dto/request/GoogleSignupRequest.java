package com.ektrepha.auth.dto.request;

import com.ektrepha.model.UserType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record GoogleSignupRequest(
		@NotBlank String idToken,
		@NotNull UserType role) {
}
