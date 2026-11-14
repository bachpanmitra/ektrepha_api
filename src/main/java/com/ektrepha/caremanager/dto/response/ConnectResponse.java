package com.ektrepha.caremanager.dto.response;

import java.util.List;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/** docs/api/openapi.yaml#/components/schemas/ConnectResponse */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ConnectResponse(ConnectedCounts connected, List<SkippedItem> skipped, CareManagerAdminResponse careManager) {

	@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
	public record ConnectedCounts(int parents, int nannies) {
	}

	@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
	public record SkippedItem(String id, String code) {
	}

}
