package com.ektrepha.caremanager.dto.response;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/** docs/api/openapi.yaml#/components/schemas/CmSummary */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CmSummaryResponse(
		long tasksDueToday,
		long overdue,
		long openComplaints,
		long trialFamilies,
		long totalFamilies,
		boolean onDuty) {
}
