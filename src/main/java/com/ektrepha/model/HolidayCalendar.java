package com.ektrepha.model;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Ops-editable holiday reference table backing {@code DayTypeResolver} (design doc open question
 * #3 - static table, no external calendar dependency). Keyed by a surrogate id rather than
 * holiday_date alone, since two different regions (e.g. Karnataka vs. Haryana) can each have their
 * own holiday on the same date - uniqueness is enforced on (holiday_date, region) instead.
 * region='ALL' marks a national holiday that applies regardless of zone.
 */
@Entity
@Table(name = "holiday_calendar")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HolidayCalendar {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "holiday_date", nullable = false)
	private LocalDate holidayDate;

	@Column(name = "name", nullable = false)
	private String name;

	@Column(name = "region", nullable = false)
	private String region;

	@Column(name = "is_active", nullable = false)
	private boolean active;

}
