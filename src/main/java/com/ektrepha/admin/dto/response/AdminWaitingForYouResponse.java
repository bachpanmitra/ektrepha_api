package com.ektrepha.admin.dto.response;

/** The Today dashboard's "Waiting for you" panel — one count per pending-review queue. */
public record AdminWaitingForYouResponse(
		long pendingLeave,
		long pendingShiftChanges,
		long pendingAttendanceCorrections,
		long pendingDocuments) {
}
