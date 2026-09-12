package com.ektrepha.repository;

import java.time.LocalDate;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ektrepha.model.HolidayCalendar;

public interface HolidayCalendarRepository extends JpaRepository<HolidayCalendar, LocalDate> {

	boolean existsByHolidayDateAndActiveTrue(LocalDate holidayDate);

}
