package com.ektrepha.nannyapp.dto.response;

import java.time.LocalDate;

/** status: PENDING | APPROVED | DECLINED. */
public record LeaveRequestResponse(
		String id, String type, LocalDate fromDate, LocalDate toDate, long daysCount, int shiftsCount, String status) {
}
