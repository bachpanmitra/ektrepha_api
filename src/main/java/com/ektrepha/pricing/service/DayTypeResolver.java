package com.ektrepha.pricing.service;

import java.time.LocalDate;

import com.ektrepha.model.DayType;

public interface DayTypeResolver {

	/**
	 * HOLIDAY &gt; WEEKEND &gt; WEEKDAY — a holiday that falls on a weekend still resolves to HOLIDAY.
	 * {@code region} matches {@code zone_areas.state} — a holiday resolves for a date if it's
	 * declared for that exact region or for region 'ALL' (national).
	 */
	DayType resolve(LocalDate date, String region);

}
