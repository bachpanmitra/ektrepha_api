package com.ektrepha.admin.dto.response;

/** One row of the Today dashboard's "Absences today" panel — a nanny on approved leave covering today. */
public record AdminAbsenceTodayResponse(
		Long nannyId,
		String nannyName,
		String reason) {
}
