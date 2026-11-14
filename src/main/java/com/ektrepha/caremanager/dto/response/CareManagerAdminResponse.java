package com.ektrepha.caremanager.dto.response;

import java.time.Instant;

import com.ektrepha.model.CmArea;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/** docs/api/openapi.yaml#/components/schemas/CareManagerAdmin */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CareManagerAdminResponse(
		String id,
		String name,
		String phone,
		String email,
		String photoUrl,
		CmArea area,
		boolean active,
		int maxParents,
		long familiesCount,
		long nanniesCount,
		String shiftStart,
		String shiftEnd,
		boolean onShift,
		Instant createdAt) {
}
