package com.ektrepha.serviceability.dto.request;

import jakarta.validation.constraints.NotNull;

public record ZoneStatusRequest(@NotNull Boolean active) {
}
