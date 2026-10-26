package com.ektrepha.booking.dto.response;

import java.time.LocalDate;
import java.time.LocalTime;

import com.ektrepha.model.LiveInArrangement;

/** A live-in stay's terms - shown to the parent after booking and to the helper on every day of the stay. */
public record LiveInArrangementSummary(
		LocalDate startDate,
		LocalDate endDate,
		int months,
		String weeklyOffDay,
		LocalTime dutyStartTime,
		LocalTime dutyEndTime,
		String accommodation,
		boolean mealsProvided) {

	public static LiveInArrangementSummary from(LiveInArrangement arrangement) {
		return new LiveInArrangementSummary(arrangement.getStartDate(), arrangement.getEndDate(), arrangement.getMonths(),
				arrangement.getWeeklyOffDay() == null ? null : arrangement.getWeeklyOffDay().name(),
				arrangement.getDutyStartTime(), arrangement.getDutyEndTime(), arrangement.getAccommodation().name(),
				arrangement.isMealsProvided());
	}

}
