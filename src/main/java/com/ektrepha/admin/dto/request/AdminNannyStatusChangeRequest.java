package com.ektrepha.admin.dto.request;

import com.ektrepha.model.NannyVerificationStatus;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** PENDING is never a valid target here - only the automatic recompute path sets it. {@code reason} is required for every target (including APPROVED, so there's always a record of why an admin signed off). */
public record AdminNannyStatusChangeRequest(
		@NotNull NannyVerificationStatus status,
		@NotBlank String reason) {
}
