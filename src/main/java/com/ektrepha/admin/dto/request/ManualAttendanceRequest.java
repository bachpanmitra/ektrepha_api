package com.ektrepha.admin.dto.request;

import java.time.LocalTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Ops manual check-in/check-out when the parent can't give the code (OTP Rules sheet rule 12). */
public record ManualAttendanceRequest(@NotNull LocalTime time, @NotBlank String reason) {
}
