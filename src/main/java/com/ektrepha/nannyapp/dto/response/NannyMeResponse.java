package com.ektrepha.nannyapp.dto.response;

import java.time.LocalDate;

/** Matches ektrepha-nanny-ui's {@code NannyMe} (api/types.ts). employmentStatus: ONBOARDING | ACTIVE | SUSPENDED | EXITED. */
public record NannyMeResponse(
		Long id, String employeeId, String name, String phone, String managerName, String managerPhone,
		String employmentStatus, LocalDate startDate, String language, double rating, int onTimePercent,
		int trainingsCompleted, LocalDate firstAidRefresherDueDate) {
}
