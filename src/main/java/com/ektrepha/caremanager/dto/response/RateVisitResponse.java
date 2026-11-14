package com.ektrepha.caremanager.dto.response;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/** docs/api/openapi.yaml#/components/schemas/RateVisitResponse */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record RateVisitResponse(int score, boolean cmWillCall, Integer callWithinMinutes) {
}
