package com.ektrepha.model;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Ops-editable holiday reference table backing {@code DayTypeResolver} (design doc open question #3 - static table, no external calendar dependency). */
@Entity
@Table(name = "holiday_calendar")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HolidayCalendar {

	@Id
	@Column(name = "holiday_date", nullable = false)
	private LocalDate holidayDate;

	@Column(name = "name", nullable = false)
	private String name;

	@Column(name = "region")
	private String region;

	@Column(name = "is_active", nullable = false)
	private boolean active;

}
