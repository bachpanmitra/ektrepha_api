package com.ektrepha.nannyapp.dto.response;

public record LeaveBalanceResponse(
		int casualDaysLeft, int sickDaysLeft, int earnedDaysLeft) {
}
