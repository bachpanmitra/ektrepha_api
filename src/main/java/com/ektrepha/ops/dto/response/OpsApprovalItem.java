package com.ektrepha.ops.dto.response;

import java.time.Instant;
import java.time.LocalDate;

/**
 * One pending request from the manager's team. {@code type} is LEAVE, SHIFT_CHANGE, ATTENDANCE or
 * PROBLEM; {@code id} is that request's own id, approved/rejected/resolved through the existing
 * /api/v1/admin/approvals/... and /api/v1/admin/safety/incidents/... endpoints. Leave dates only on LEAVE.
 */
public record OpsApprovalItem(
		String type,
		Long id,
		Long nannyId,
		String nannyName,
		String role,
		String details,
		Long bookingId,
		LocalDate fromDate,
		LocalDate toDate,
		Instant createdAt) {
}
