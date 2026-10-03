package com.ektrepha.verification.dto.request;

import java.time.Instant;
import java.util.Map;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

/** {@code answers} maps a {@code QuizQuestion#id} to the selected option's index. Graded server-side - the client never sees the correct answers. */
public record TrainingAttemptSubmitRequest(
		@NotNull Instant startedAt,
		@NotEmpty Map<String, Integer> answers) {
}
