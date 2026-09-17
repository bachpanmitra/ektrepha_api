package com.ektrepha.repository;

import java.time.LocalDate;
import java.util.Collection;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ektrepha.model.HolidayCalendar;

public interface HolidayCalendarRepository extends JpaRepository<HolidayCalendar, Long> {

	boolean existsByHolidayDateAndRegionInAndActiveTrue(LocalDate holidayDate, Collection<String> regions);

}
