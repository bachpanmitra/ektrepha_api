package com.ektrepha.account.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** {@code currentPassword} is required only when the account already has one (see A4 "Google-only -> Set a password" vs "Change password"); the service decides, not this DTO. */
public record SetPasswordRequest(
		String currentPassword,
		@NotBlank @Size(min = 8, max = 100) String newPassword) {
}
