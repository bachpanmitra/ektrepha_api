package com.ektrepha.hourlycare.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Ops swaps the caregiver on a booking that's already CONFIRMED (not awaiting assignment) - the
 * emergency-replacement flow for a no-show or last-minute caregiver cancellation. Unlike the initial
 * {@link AssignCaregiverRequest}, a reason is mandatory - this overrides a caregiver someone already
 * committed to the family, so it needs to leave a record of why. */
public record ReassignCaregiverRequest(
		@Schema(description = "Id of the replacement caregiver", example = "42") @NotNull Long nannyId,
		@Schema(description = "Why the caregiver is being swapped, e.g. \"no-show\", \"caregiver fell ill\"", example = "No-show, unreachable 30 min past start time")
		@NotBlank String reason) {
}
