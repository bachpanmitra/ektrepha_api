package com.ektrepha.nannyapp.dto.response;

/** status: PRESENT | LATE | LEAVE | WEEKLY_OFF | FUTURE | NONE. */
public record AttendanceDayResponse(
		String date, String status) {
}
