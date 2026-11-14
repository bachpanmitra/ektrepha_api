package com.ektrepha.caremanager.dto.response;

import java.time.Instant;
import java.util.List;

import com.ektrepha.model.CmArea;
import com.ektrepha.model.ParentStage;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/** docs/api/openapi.yaml#/components/schemas/ParentListItem */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ParentListItemResponse(
		String id,
		String name,
		String phoneMasked,
		CmArea area,
		ParentStage stage,
		Integer trialDay,
		PersonRefResponse nanny,
		PersonRefResponse careManager,
		Instant lastContactAt,
		List<String> openIssues) {
}
