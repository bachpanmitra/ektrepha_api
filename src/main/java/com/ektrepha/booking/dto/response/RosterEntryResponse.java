package com.ektrepha.booking.dto.response;

import java.time.Instant;
import java.time.LocalDate;

/**
 * One day in the nanny's roster (API Map row 29). {@code bookingType} is a best-effort mapping
 * from {@code Booking.frequency} (ONE_TIME/REPEAT_WEEKLY -&gt; HOURLY, REPEAT_DAILY -&gt; LIVE_IN,
 * REPEAT_MONTHLY -&gt; MONTHLY) rather than a dedicated field, since no such field exists yet.
 * {@code attendanceStatus} is null when no {@code booking_attendance} row exists for the day yet
 * (future days haven't had the daily code-generation job run for them).
 */
public record RosterEntryResponse(
		Long bookingId,
		LocalDate date,
		String dayStatus,
		Instant startTime,
		Instant endTime,
		String bookingType,
		String serviceType,
		String recipientName,
		String attendanceStatus) {
}
