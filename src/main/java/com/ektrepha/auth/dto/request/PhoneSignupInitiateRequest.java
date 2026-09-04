package com.ektrepha.auth.dto.request;

import com.ektrepha.model.UserType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * The PRD's request for this endpoint is {phoneNumber, password} only, but
 * user_type is NOT NULL on users and nothing later in the phone-signup flow
 * ever supplies it — role is collected here instead so the account can
 * actually be created at the verify step.
 */
public record PhoneSignupInitiateRequest(
		@NotBlank String phoneNumber,
		@NotBlank @Size(min = 8, max = 100) String password,
		@NotNull UserType role) {
}
