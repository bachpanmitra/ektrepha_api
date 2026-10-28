package com.ektrepha.ops.dto.response;

import java.time.Instant;

/**
 * One helper on an ops manager's team board. The booking fields describe the duty the status is
 * about (today's current or next one) and are null when there is none. {@code careModel} is
 * LIVE_IN, MONTHLY or VISIT; {@code liveInDay}/{@code liveInDays} ("day 8 of 30") only on a live-in
 * day. {@code needsCover} = the family needs someone sent: the helper is late, or on leave while
 * still assigned to today's duty.
 */
public record OpsTeamMemberResponse(
		Long nannyId,
		String name,
		String phone,
		String role,
		String status,
		boolean needsCover,
		Long openSosId,
		Long bookingId,
		String careModel,
		String careRecipient,
		String area,
		Instant startTime,
		Instant endTime,
		Instant checkedInAt,
		Integer liveInDay,
		Integer liveInDays) {
}
