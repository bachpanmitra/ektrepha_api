package com.ektrepha.workforce.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Body shared by check-in and check-out — {@code otp} is matched against that side's own hash, see {@code BookingAttendance}. */
public record CheckInRequest(
		@NotBlank String otp,
		@NotNull Double lat,
		@NotNull Double lng) {
}
