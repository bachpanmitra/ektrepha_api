package com.ektrepha.ops.dto.response;

import java.util.List;

/** The ops manager's "Team today" screen - counts for their own team only, plus who needs them now. */
public record OpsTeamTodayResponse(
		Counts counts,
		RoleCounts roles,
		List<OpsTeamMemberResponse> needsAttention,
		WaitingForYou waitingForYou) {

	public record Counts(int total, int onDuty, int notStarted, int done, int offOrLeave, int sos, int needCover) {
	}

	public record RoleCounts(int nannies, int elderCarers, int nurses) {
	}

	/** Pending requests from this team only - the same queues as the admin dashboard's "Waiting for you". */
	public record WaitingForYou(int leave, int shiftChanges, int attendanceCorrections, int problems) {
	}

}
