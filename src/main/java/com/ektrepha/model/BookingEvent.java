package com.ektrepha.model;

import java.time.Instant;
import java.time.LocalDate;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Append-only event log for a booking's day — the single shared write path for "on the way" tracking (TRIP_STARTED/ARRIVED today; SOS/incidents in a later pass). See {@code BookingEventService}. */
@Entity
@Table(name = "booking_event")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookingEvent {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "booking_id", nullable = false)
	private Booking booking;

	@Column(name = "event_date", nullable = false)
	private LocalDate eventDate;

	@Column(name = "type", nullable = false)
	@Enumerated(EnumType.STRING)
	private BookingEventType type;

	@Column(name = "lat")
	private Double lat;

	@Column(name = "lng")
	private Double lng;

	@Column(name = "actor", nullable = false)
	@Enumerated(EnumType.STRING)
	private BookingEventActor actor;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "meta", columnDefinition = "jsonb")
	private String meta;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@PrePersist
	void onCreate() {
		this.createdAt = Instant.now();
	}

}
