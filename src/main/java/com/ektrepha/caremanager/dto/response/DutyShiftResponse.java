package com.ektrepha.caremanager.dto.response;

import java.time.Instant;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/** docs/api/openapi.yaml#/components/schemas/DutyShift */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record DutyShiftResponse(String id, String careManagerId, Instant startsAt, Instant endsAt) {
}
