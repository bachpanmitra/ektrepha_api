package com.ektrepha.pricing.service;

import java.time.LocalDate;

import com.ektrepha.model.DayType;

public interface DayTypeResolver {

	/** HOLIDAY &gt; WEEKEND &gt; WEEKDAY — a holiday that falls on a weekend still resolves to HOLIDAY. */
	DayType resolve(LocalDate date);

}
