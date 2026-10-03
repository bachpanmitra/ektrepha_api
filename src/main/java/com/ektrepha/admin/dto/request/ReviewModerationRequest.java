package com.ektrepha.admin.dto.request;

import com.ektrepha.model.ReviewStatus;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** {@code reason} is required for both directions - hiding needs a record of why, and so does
 * restoring one (e.g. "re-reviewed, not actually abusive"). */
public record ReviewModerationRequest(
		@NotNull ReviewStatus status,
		@NotBlank String reason) {
}
