package com.ektrepha.bookingrequest.dto.request;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record BookingRequestCreateRequest(
		@NotNull Long userId,
		@NotNull Long zoneAreaId,
		@NotBlank String serviceTypeCode,
		@NotNull LocalDate bookingDate,
		@NotNull LocalTime startTime,
		@NotNull LocalTime endTime,
		BigDecimal quotedTotal) {
}
