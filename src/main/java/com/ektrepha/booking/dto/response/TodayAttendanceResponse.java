package com.ektrepha.booking.dto.response;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Parent's view of today's codes (API Spec sheet row 13). {@code checkInOtp} is shown only while
 * the day is still PENDING; {@code checkOutOtp} only once CHECKED_IN (OTP Rules sheet rule 8) —
 * both null again once CHECKED_OUT, or on an OFF_DAY (approved leave).
 */
public record TodayAttendanceResponse(
		LocalDate date,
		String status,
		String checkInOtp,
		String checkOutOtp,
		Instant checkedInAt,
		Instant checkedOutAt) {
}
