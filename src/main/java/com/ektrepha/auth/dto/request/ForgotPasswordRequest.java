package com.ektrepha.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Phone-based reset uses a separate endpoint — see ForgotPasswordPhoneRequest. */
public record ForgotPasswordRequest(@NotBlank @Email String email) {
}
