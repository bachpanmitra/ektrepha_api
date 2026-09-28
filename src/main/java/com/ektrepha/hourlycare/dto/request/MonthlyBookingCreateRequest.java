package com.ektrepha.hourlycare.dto.request;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Review screen's "Continue to payment" for monthly care - reserves every occurrence as AWAITING_PAYMENT. */
public record MonthlyBookingCreateRequest(
		Long childId,
		@NotNull Long addressId,
		@NotNull LocalDate startDate,
		@NotNull LocalDate endDate,
		@NotEmpty Set<DayOfWeek> daysOfWeek,
		@NotNull LocalTime dailyStartTime,
		@NotNull LocalTime dailyEndTime,
		@Size(max = 500) String careNotes) {
}
