package com.ektrepha.caremanager.dto.response;

import com.ektrepha.model.NannyOperationalStatus;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/** docs/api/openapi.yaml#/components/schemas/NannyListItem */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record NannyListItemResponse(
		String id,
		String name,
		String phoneMasked,
		NannyOperationalStatus status,
		Double avgRating,
		long lowRatingsCount,
		long familiesCount,
		PersonRefResponse careManager) {
}
