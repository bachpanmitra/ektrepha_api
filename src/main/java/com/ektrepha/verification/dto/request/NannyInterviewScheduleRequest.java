package com.ektrepha.verification.dto.request;

import java.time.Instant;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;

public record NannyInterviewScheduleRequest(
		@NotNull Long nannyId,
		@NotNull @Future Instant scheduledAt) {
}
