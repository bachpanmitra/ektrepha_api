package com.ektrepha.caremanager.dto.response;

import java.time.Instant;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/** docs/api/openapi.yaml#/components/schemas/DutyShift */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record DutyShiftResponse(String id, String careManagerId, Instant startsAt, Instant endsAt) {
}
