package com.ektrepha.auth.dto.request;

import jakarta.validation.constraints.NotBlank;

public record ForgotPasswordPhoneRequest(@NotBlank String phoneNumber) {
}
