package com.ektrepha.hourlycare.dto.request;

import jakarta.validation.constraints.NotNull;

/** Ops picks the in-house caregiver to actually assign, once payment has moved a booking to ASSIGNING_CAREGIVER. */
public record AssignCaregiverRequest(@NotNull Long nannyId) {
}
