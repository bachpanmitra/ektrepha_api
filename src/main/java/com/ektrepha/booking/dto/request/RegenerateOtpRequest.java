package com.ektrepha.booking.dto.request;

import com.ektrepha.model.AttendanceOtpType;

import jakarta.validation.constraints.NotNull;

public record RegenerateOtpRequest(@NotNull AttendanceOtpType type) {
}
