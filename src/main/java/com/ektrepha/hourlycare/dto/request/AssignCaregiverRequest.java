package com.ektrepha.hourlycare.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/** Ops picks the in-house caregiver to actually assign, once payment has moved a booking to ASSIGNING_CAREGIVER. */
public record AssignCaregiverRequest(
		@Schema(description = "Id of the in-house caregiver to assign", example = "42") @NotNull Long nannyId) {
}
