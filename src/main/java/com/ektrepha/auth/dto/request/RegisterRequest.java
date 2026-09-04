package com.ektrepha.auth.dto.request;

import com.ektrepha.model.UserType;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
		@NotBlank @Email String email,
		@NotBlank String name,
		@NotBlank @Size(min = 8, max = 100) String password,
		@NotBlank String phoneNumber,
		@NotNull UserType role) {
}
