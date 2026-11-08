package com.ektrepha.booking.dto.response;

import java.time.Instant;
import java.time.LocalDate;

/**
 * One day's row in a month attendance view (API Spec sheet rows 2, 12, 15) — one shared shape for
 * the admin, nanny and parent "attendance?month=" endpoints. {@code manualByUserId} is populated
 * for the admin view and left null for nanny/parent (API Spec sheet row 12: "no manualByUserId").
 */
public record AttendanceDayEntry(
		LocalDate date,
		String status,
		Instant checkedInAt,
		Instant checkedOutAt,
		Long minutes,
		String checkInMethod,
		String checkOutMethod,
		Long manualByUserId) {
}
