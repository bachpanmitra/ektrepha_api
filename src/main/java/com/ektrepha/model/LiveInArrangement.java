package com.ektrepha.model;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The terms of one live-in stay (migration 048) - what the family provides and the helper's
 * working hours. The stay's actual days are ordinary {@link Booking} rows (frequency LIVE_IN, one
 * per on-duty day) sharing {@link #anchorBooking}'s id as their recurrenceGroupId.
 */
@Entity
@Table(name = "live_in_arrangement")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LiveInArrangement {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "anchor_booking_id", nullable = false, unique = true)
	private Booking anchorBooking;

	@Column(name = "start_date", nullable = false)
	private LocalDate startDate;

	@Column(name = "end_date", nullable = false)
	private LocalDate endDate;

	@Column(name = "months", nullable = false)
	private int months;

	/** Null when the family has no fixed weekly off for the helper. */
	@Column(name = "weekly_off_day", length = 10)
	@Enumerated(EnumType.STRING)
	private DayOfWeek weeklyOffDay;

	@Column(name = "duty_start_time", nullable = false)
	private LocalTime dutyStartTime;

	@Column(name = "duty_end_time", nullable = false)
	private LocalTime dutyEndTime;

	@Column(name = "accommodation", nullable = false, length = 20)
	@Enumerated(EnumType.STRING)
	private LiveInAccommodation accommodation;

	@Column(name = "meals_provided", nullable = false)
	private boolean mealsProvided;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@PrePersist
	void onCreate() {
		this.createdAt = Instant.now();
	}

}
