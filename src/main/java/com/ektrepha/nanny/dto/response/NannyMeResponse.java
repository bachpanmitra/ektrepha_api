package com.ektrepha.nanny.dto.response;

import java.time.LocalDate;

/**
 * The nanny app's own profile screen (ektrepha-nanny-ui's NannyMe) - every field is sourced from
 * a real column/relation on {@link com.ektrepha.model.Nanny}; nothing here is fabricated. Two
 * fields the app's type expects have no backing concept at all yet and are deliberately left at
 * their honest empty value rather than invented: {@code onTimePercent} (no attendance-punctuality
 * metric is computed anywhere today) and {@code firstAidRefresherDueDate} (no refresher-cadence
 * concept exists) - see api/adapters.ts's Stay comment for the same "explicit empty sentinel"
 * convention on the app side.
 */
public record NannyMeResponse(
		Long id,
		String employeeId,
		String name,
		String phone,
		String managerName,
		String managerPhone,
		String employmentStatus,
		LocalDate startDate,
		String language,
		double rating,
		double onTimePercent,
		int trainingsCompleted,
		LocalDate firstAidRefresherDueDate) {
}
