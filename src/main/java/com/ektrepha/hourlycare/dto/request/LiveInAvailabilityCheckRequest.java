package com.ektrepha.hourlycare.dto.request;

import java.time.DayOfWeek;
import java.time.LocalDate;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Live-in care Details screen's "Check availability" - a helper living in the home for whole
 * months from startDate, off on weeklyOffDay (null = no fixed weekly off). childId/elderId follow
 * the same rules as {@link AvailabilityCheckRequest}.
 */
public record LiveInAvailabilityCheckRequest(
		Long childId,
		@NotNull Long addressId,
		@NotNull LocalDate startDate,
		@NotNull @Min(1) @Max(3) Integer months,
		DayOfWeek weeklyOffDay,
		String serviceTypeCode,
		Long elderId) {
}
