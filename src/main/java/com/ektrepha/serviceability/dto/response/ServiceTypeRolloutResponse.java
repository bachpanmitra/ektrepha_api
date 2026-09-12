package com.ektrepha.serviceability.dto.response;

import java.time.Instant;

import com.ektrepha.model.ServiceabilityStatus;

public record ServiceTypeRolloutResponse(
		Long zoneAreaId,
		Long serviceTypeId,
		String serviceTypeCode,
		ServiceabilityStatus status,
		Instant launchedAt) {
}
