package com.ektrepha.admin.dto.response;

import java.util.List;

/**
 * {@code lateOrNoCheckIn} is always 0 — no check-in/attendance tracking exists yet (lands with the
 * Roster/Attendance phase). {@code nanniesOnLeave}, {@code openSosCount} and
 * {@code pendingApprovalsCount} are real, backed by the Phase 5 (Approvals & Safety) tables.
 */
public record AdminDashboardTodayResponse(
		long shiftsToday,
		long liveNow,
		long needingAssignment,
		long lateOrNoCheckIn,
		long nanniesOnLeave,
		long openSosCount,
		long pendingApprovalsCount,
		List<AdminBookingSummaryResponse> bookingsNeedingAssignment,
		List<AdminLiveShiftResponse> liveShifts) {
}
