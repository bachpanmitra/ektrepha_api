package com.ektrepha.booking.dto.response;

import java.util.List;

public record AttendanceMonthResponse(Long bookingId, String month, List<AttendanceDayEntry> days, AttendanceMonthTotals totals) {
}
