package com.ektrepha.workforce.dto.response;

import java.time.Instant;
import java.time.LocalDate;

/** One {@code booking_attendance} row, nanny/parent-facing shape (API Spec sheet rows 10, 11, 13). {@code minutes} is null until checked out. */
public record AttendanceResponse(
		Long bookingId,
		LocalDate date,
		String status,
		Instant checkedInAt,
		Instant checkedOutAt,
		String checkInMethod,
		String checkOutMethod,
		Long minutes) {
}
