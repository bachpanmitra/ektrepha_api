package com.ektrepha.caremanager.dto.response;

import java.time.Instant;
import java.util.List;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/** docs/api/openapi.yaml#/components/schemas/NannyDetail.verification */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record NannyVerificationSummaryResponse(
		Instant idVerifiedAt,
		Instant policeVerifiedAt,
		Instant referencesCheckedAt,
		Instant interviewedAt,
		List<DocumentResponse> documents) {

	@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
	public record DocumentResponse(String type, String url) {
	}

}
