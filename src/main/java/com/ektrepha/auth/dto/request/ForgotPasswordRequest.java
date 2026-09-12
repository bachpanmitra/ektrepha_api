package com.ektrepha.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Phone-based reset no longer needs an "initiate" step — see PhoneResetPasswordRequest. */
public record ForgotPasswordRequest(@NotBlank @Email String email) {
}
