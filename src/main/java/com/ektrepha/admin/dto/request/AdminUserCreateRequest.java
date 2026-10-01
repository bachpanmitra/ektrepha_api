package com.ektrepha.admin.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Invites a new Ops admin by email — there is no public signup path for ADMIN accounts (see
 * {@code AuthServiceImpl#register}, which deliberately refuses the role). The new account gets no
 * password, so it can't log in until the invitee runs the existing email/OTP forgot-password flow
 * at {@code /forgot-password} — that's the whole "invite", no separate token/email system needed.
 */
public record AdminUserCreateRequest(
		@NotBlank String name,
		@NotBlank @Email String email) {
}
