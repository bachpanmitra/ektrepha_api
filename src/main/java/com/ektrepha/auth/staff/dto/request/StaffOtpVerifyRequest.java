package com.ektrepha.auth.staff.dto.request;

import jakarta.validation.constraints.NotBlank;

public record StaffOtpVerifyRequest(@NotBlank String phone, @NotBlank String code) {
}
