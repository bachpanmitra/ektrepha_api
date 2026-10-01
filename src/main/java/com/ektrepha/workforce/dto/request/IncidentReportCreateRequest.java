package com.ektrepha.workforce.dto.request;

import jakarta.validation.constraints.NotBlank;

public record IncidentReportCreateRequest(Long bookingId, Long nannyId, @NotBlank String description) {
}
