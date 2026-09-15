package com.ektrepha.bookingrequest.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

import com.ektrepha.model.BookingFrequency;

/** One row in the caller's "My bookings" list. */
public record BookingRequestSummaryResponse(
		Long id,
		String serviceTypeCode,
		String serviceTypeName,
		String zoneName,
		String city,
		LocalDate bookingDate,
		LocalTime startTime,
		LocalTime endTime,
		BigDecimal quotedTotal,
		BookingFrequency frequency,
		short childrenCount,
		Short childAgeYears,
		String careNotes,
		Instant createdAt) {
}
