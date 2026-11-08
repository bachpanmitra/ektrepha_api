package com.ektrepha.booking.impl;

import java.time.Duration;
import java.util.List;

import org.springframework.stereotype.Component;

import com.ektrepha.booking.dto.response.AttendanceDayEntry;
import com.ektrepha.booking.dto.response.AttendanceMonthResponse;
import com.ektrepha.booking.dto.response.AttendanceMonthTotals;
import com.ektrepha.model.AttendanceStatus;
import com.ektrepha.model.BookingAttendance;

/** Shared shape for the nanny/parent/admin month attendance endpoints (API Spec sheet rows 2, 12, 15). */
@Component
public class AttendanceMonthMapper {

	public AttendanceMonthResponse toResponse(Long bookingId, String month, List<BookingAttendance> rows, boolean includeManualByUserId) {
		List<AttendanceDayEntry> days = rows.stream().map(row -> toDay(row, includeManualByUserId)).toList();
		long daysWorked = rows.stream().filter(row -> row.getStatus() == AttendanceStatus.CHECKED_OUT).count();
		long offDays = rows.stream().filter(row -> row.getStatus() == AttendanceStatus.OFF_DAY).count();
		long absent = rows.stream().filter(row -> row.getStatus() == AttendanceStatus.ABSENT).count();
		return new AttendanceMonthResponse(bookingId, month, days, new AttendanceMonthTotals(daysWorked, offDays, absent));
	}

	private AttendanceDayEntry toDay(BookingAttendance row, boolean includeManualByUserId) {
		Long minutes = (row.getCheckedInAt() != null && row.getCheckedOutAt() != null)
				? Duration.between(row.getCheckedInAt(), row.getCheckedOutAt()).toMinutes()
				: null;
		Long manualByUserId = includeManualByUserId && row.getManualByUser() != null ? row.getManualByUser().getId() : null;
		return new AttendanceDayEntry(row.getAttendanceDate(), row.getStatus().name(), row.getCheckedInAt(), row.getCheckedOutAt(),
				minutes, row.getCheckInMethod() == null ? null : row.getCheckInMethod().name(),
				row.getCheckOutMethod() == null ? null : row.getCheckOutMethod().name(), manualByUserId);
	}

}
