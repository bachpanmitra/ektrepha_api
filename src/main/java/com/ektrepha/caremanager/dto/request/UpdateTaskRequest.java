package com.ektrepha.caremanager.dto.request;

import com.ektrepha.model.CmTaskStatus;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** docs/api/openapi.yaml#/components/schemas/UpdateTaskRequest */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record UpdateTaskRequest(@NotNull CmTaskStatus status, @Size(max = 1000) String note) {
}
