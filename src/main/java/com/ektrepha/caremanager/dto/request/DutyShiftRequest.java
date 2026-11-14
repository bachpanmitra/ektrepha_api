package com.ektrepha.caremanager.dto.request;

import java.time.Instant;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** docs/api/openapi.yaml#/components/schemas/DutyShiftInput */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record DutyShiftRequest(@NotBlank String careManagerId, @NotNull Instant startsAt, @NotNull Instant endsAt) {
}
