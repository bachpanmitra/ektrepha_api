package com.ektrepha.bookingrequest.dto.request;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

import com.ektrepha.model.BookingFrequency;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record BookingRequestCreateRequest(
		@NotNull Long userId,
		@NotNull Long zoneAreaId,
		@NotBlank String serviceTypeCode,
		@NotNull LocalDate bookingDate,
		@NotNull LocalTime startTime,
		@NotNull LocalTime endTime,
		BigDecimal quotedTotal,
		@NotNull BookingFrequency frequency,
		@NotNull @Min(1) Integer childrenCount,
		@Min(0) Integer childAgeYears,
		@Size(max = 500) String careNotes) {
}
