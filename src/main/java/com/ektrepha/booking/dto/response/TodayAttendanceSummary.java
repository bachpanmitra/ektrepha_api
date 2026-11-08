package com.ektrepha.booking.dto.response;

import java.time.Instant;
import java.time.LocalDate;

/** Nanny-facing today's-attendance summary on {@code NannyTodayBookingResponse} (API Spec sheet row 4) — null when no {@code booking_attendance} row exists yet for today. */
public record TodayAttendanceSummary(LocalDate date, String status, Instant checkedInAt, Instant checkedOutAt) {
}
