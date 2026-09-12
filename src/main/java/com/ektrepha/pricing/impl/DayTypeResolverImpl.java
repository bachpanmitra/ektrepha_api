package com.ektrepha.pricing.impl;

import java.time.DayOfWeek;
import java.time.LocalDate;

import org.springframework.stereotype.Component;

import com.ektrepha.model.DayType;
import com.ektrepha.repository.HolidayCalendarRepository;
import com.ektrepha.pricing.service.DayTypeResolver;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class DayTypeResolverImpl implements DayTypeResolver {

	private final HolidayCalendarRepository holidayCalendarRepository;

	@Override
	public DayType resolve(LocalDate date) {
		if (holidayCalendarRepository.existsByHolidayDateAndActiveTrue(date)) {
			return DayType.HOLIDAY;
		}
		DayOfWeek dayOfWeek = date.getDayOfWeek();
		return (dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY) ? DayType.WEEKEND : DayType.WEEKDAY;
	}

}
