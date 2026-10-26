package com.ektrepha.hourlycare.dto.request;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;

import com.ektrepha.model.LiveInAccommodation;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Live-in care Review screen's "Continue to payment" - reserves every on-duty day as
 * AWAITING_PAYMENT and records what the family provides (room, meals) and the helper's daily
 * working hours.
 */
public record LiveInBookingCreateRequest(
		Long childId,
		@NotNull Long addressId,
		@NotNull LocalDate startDate,
		@NotNull @Min(1) @Max(3) Integer months,
		DayOfWeek weeklyOffDay,
		@NotNull LocalTime dutyStartTime,
		@NotNull LocalTime dutyEndTime,
		@NotNull LiveInAccommodation accommodation,
		@NotNull Boolean mealsProvided,
		@Size(max = 500) String careNotes,
		String serviceTypeCode,
		Long elderId) {
}
