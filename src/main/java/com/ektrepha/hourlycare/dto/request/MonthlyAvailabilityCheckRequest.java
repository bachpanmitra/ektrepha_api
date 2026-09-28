package com.ektrepha.hourlycare.dto.request;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

/**
 * Monthly care Details screen's "Review care schedule" - a bounded recurring series (team assigns,
 * like hourly/daily care) that happens on the selected weekdays between startDate and endDate
 * (inclusive of both). childId is optional, same as {@link AvailabilityCheckRequest}.
 */
public record MonthlyAvailabilityCheckRequest(
		Long childId,
		@NotNull Long addressId,
		@NotNull LocalDate startDate,
		@NotNull LocalDate endDate,
		@NotEmpty Set<DayOfWeek> daysOfWeek,
		@NotNull LocalTime dailyStartTime,
		@NotNull LocalTime dailyEndTime) {
}
