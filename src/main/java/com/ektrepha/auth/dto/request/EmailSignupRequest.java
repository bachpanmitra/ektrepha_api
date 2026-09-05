package com.ektrepha.auth.dto.request;

import com.ektrepha.model.UserType;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * The PRD's request for this endpoint is {email, password} only, but
 * user_type is NOT NULL on users — role is collected here for the same
 * reason as PhoneSignupInitiateRequest.
 */
public record EmailSignupRequest(
		@NotBlank @Email String email,
		@NotBlank @Size(min = 8, max = 100) String password,
		@NotNull UserType role) {
}
