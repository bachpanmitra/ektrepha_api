package com.ektrepha.account.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record EmailChangeVerifyRequest(@NotBlank @Email String newEmail, @NotBlank String otp) {
}
