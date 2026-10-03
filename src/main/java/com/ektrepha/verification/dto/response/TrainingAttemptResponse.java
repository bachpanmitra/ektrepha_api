package com.ektrepha.verification.dto.response;

import java.time.Instant;

public record TrainingAttemptResponse(
		Long id,
		String moduleVersion,
		int score,
		boolean passed,
		Instant completedAt) {
}
