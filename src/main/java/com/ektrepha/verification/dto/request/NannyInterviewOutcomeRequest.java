package com.ektrepha.verification.dto.request;

import com.ektrepha.model.InterviewOutcome;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record NannyInterviewOutcomeRequest(
		@NotNull InterviewOutcome outcome,
		@Size(max = 1000) String notes) {
}
