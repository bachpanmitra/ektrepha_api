package com.ektrepha.nannyapp.dto.response;

import java.time.Instant;

/** status: ACTIVE | CANCELLED. emergencyContactNotifiedAt stays null - there is no SMS delivery to the emergency contact yet. */
public record SosEventResponse(
		String id, String status, Instant createdAt, Instant safetyTeamAlertedAt, Instant emergencyContactNotifiedAt) {
}
