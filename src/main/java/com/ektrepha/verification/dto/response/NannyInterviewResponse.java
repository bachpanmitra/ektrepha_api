package com.ektrepha.verification.dto.response;

import java.time.Instant;

public record NannyInterviewResponse(
		Long id,
		Long nannyId,
		Instant scheduledAt,
		String outcome,
		Instant conductedAt,
		String notes) {
}
