package com.ektrepha.nannyapp.dto.response;

import java.time.LocalDate;

/** An HR-scheduled training session. */
public record TrainingResponse(
		String id, String title, LocalDate startDate, String location, int durationDays, boolean completed) {
}
