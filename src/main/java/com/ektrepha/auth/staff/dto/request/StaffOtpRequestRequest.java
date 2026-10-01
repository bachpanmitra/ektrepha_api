package com.ektrepha.auth.staff.dto.request;

import jakarta.validation.constraints.NotBlank;

public record StaffOtpRequestRequest(@NotBlank String phone) {
}
