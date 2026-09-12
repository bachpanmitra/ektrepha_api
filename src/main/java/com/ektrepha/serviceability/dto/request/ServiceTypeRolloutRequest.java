package com.ektrepha.serviceability.dto.request;

import com.ektrepha.model.ServiceabilityStatus;

import jakarta.validation.constraints.NotNull;

public record ServiceTypeRolloutRequest(@NotNull ServiceabilityStatus status) {
}
