package com.ektrepha.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One row per nanny (not per booking) — a nanny only ever has one active "on the way" trip at a
 * time, and the latest ping always wins regardless of which booking it's attached to. Written only
 * via {@code NannyLocationRepository#upsert} (native {@code ON CONFLICT}), never through
 * {@code save()} directly — plain {@code Long} fields rather than entity relations since every
 * access pattern here is a simple upsert-by-nannyId or read-by-nannyId, not a navigable graph.
 */
@Entity
@Table(name = "nanny_location")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NannyLocation {

	@Id
	@Column(name = "nanny_id")
	private Long nannyId;

	@Column(name = "booking_id", nullable = false)
	private Long bookingId;

	@Column(name = "lat", nullable = false)
	private Double lat;

	@Column(name = "lng", nullable = false)
	private Double lng;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

}
