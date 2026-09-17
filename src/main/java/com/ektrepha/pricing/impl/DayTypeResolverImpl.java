package com.ektrepha.pricing.impl;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Component;

import com.ektrepha.model.DayType;
import com.ektrepha.repository.HolidayCalendarRepository;
import com.ektrepha.pricing.service.DayTypeResolver;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class DayTypeResolverImpl implements DayTypeResolver {

	private static final String NATIONAL_REGION = "ALL";

	private final HolidayCalendarRepository holidayCalendarRepository;

	@Override
	public DayType resolve(LocalDate date, String region) {
		if (holidayCalendarRepository.existsByHolidayDateAndRegionInAndActiveTrue(date, List.of(region, NATIONAL_REGION))) {
			return DayType.HOLIDAY;
		}
		DayOfWeek dayOfWeek = date.getDayOfWeek();
		return (dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY) ? DayType.WEEKEND : DayType.WEEKDAY;
	}

}
