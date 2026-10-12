package com.ektrepha.model;

import java.time.Instant;

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

/**
 * One row per booking cancellation (migration 042) - who cancelled it, a reportable
 * {@link BookingCancellationReasonCode} plus the free-text reason, and the status it was in right
 * beforehand. Written explicitly from every cancellation path (parent self-serve cancel, the admin
 * override-cancel, the stale-booking auto-cancel sweep) - never reconstructed from
 * {@link Booking#getCancellationReason()}, which only ever holds the current value.
 */
@Entity
@Table(name = "booking_cancellation_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookingCancellationLog {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "booking_id", nullable = false)
	private Booking booking;

	// Null for a SYSTEM-actor row (the stale auto-cancel sweep) - same nullability as Booking#cancelledBy.
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "cancelled_by_user_id")
	private User cancelledBy;

	@Column(name = "actor_type", nullable = false)
	@Enumerated(EnumType.STRING)
	private OrderActivityActorType actorType;

	@Column(name = "reason_code", nullable = false)
	@Enumerated(EnumType.STRING)
	private BookingCancellationReasonCode reasonCode;

	@Column(name = "reason", length = 500)
	private String reason;

	// BookingStatus's own AbstractCodedEnumConverter (autoApply=true) applies here too - same
	// SMALLINT encoding as Booking#status, no extra annotation needed.
	@Column(name = "previous_status", nullable = false)
	private BookingStatus previousStatus;

	@Column(name = "cancelled_at", nullable = false)
	private Instant cancelledAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@PrePersist
	void onCreate() {
		this.createdAt = Instant.now();
		if (this.cancelledAt == null) {
			this.cancelledAt = this.createdAt;
		}
	}

}
