package com.ektrepha.nannyapp.dto.response;

/** Matches the nanny app's {@code ShiftSummary}. date is YYYY-MM-DD and times HH:mm, both in IST. id is the booking id, or a synthetic {@code leave-<date>} / {@code off-<date>} for roster days with no booking. */
public record ShiftSummaryResponse(
		String id, String date, String startTime, String endTime, String kind, String status,
		String areaLabel, String childLabel) {
}
