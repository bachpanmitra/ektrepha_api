package com.ektrepha.caremanager.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** docs/api/openapi.yaml#/components/schemas/RateVisitRequest */
public record RateVisitRequest(@NotNull @Min(1) @Max(5) Integer score, @Size(max = 1000) String comment) {
}
