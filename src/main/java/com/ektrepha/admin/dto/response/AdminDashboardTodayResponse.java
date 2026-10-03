package com.ektrepha.admin.dto.response;

import java.util.List;

/**
 * {@code lateOrNoCheckIn} counts today's CONFIRMED/IN_PROGRESS shifts whose scheduled start (plus a
 * grace window) has already passed with no on-time check-in (see BookingRepository#countLateOrNoCheckInToday).
 * {@code nanniesOnLeave}, {@code openSosCount} and {@code pendingApprovalsCount} are backed by the
 * Phase 5 (Approvals & Safety) tables.
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
		List<AdminLiveShiftResponse> liveShifts,
		AdminWaitingForYouResponse waitingForYou,
		List<AdminAbsenceTodayResponse> absencesToday) {
}
