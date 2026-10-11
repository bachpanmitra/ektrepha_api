package com.ektrepha.nannyapp.dto.response;

import java.util.List;

public record AttendanceSummaryResponse(
		String month, int daysPresent, int daysLate, int daysLeave, double overtimeHours,
		List<AttendanceDayResponse> days) {
}
