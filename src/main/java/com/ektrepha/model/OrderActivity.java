package com.ektrepha.model;

import java.time.Instant;

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

/**
 * One timestamped event in a booking's lifecycle (migration 039) - the audit trail behind the admin
 * "Activity" timeline. Written explicitly by {@code OrderActivityServiceImpl} at each real write
 * point (assign caregiver, nanny check-in/out, payment settle) rather than inferred from
 * {@link Booking}'s current-state columns, which can't say *when* something happened.
 */
@Entity
@Table(name = "order_activity")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderActivity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "booking_id", nullable = false)
	private Booking booking;

	@Column(name = "event_type", nullable = false)
	@Enumerated(EnumType.STRING)
	private OrderActivityType eventType;

	@Column(name = "occurred_at", nullable = false)
	private Instant occurredAt;

	@Column(name = "actor_type", nullable = false)
	@Enumerated(EnumType.STRING)
	private OrderActivityActorType actorType;

	@Column(name = "actor_id")
	private Long actorId;

	@Column(name = "actor_name")
	private String actorName;

	// Same String-backed jsonb mapping as Nanny/Children#metaData - columnDefinition alone isn't
	// enough, @JdbcTypeCode(SqlTypes.JSON) is required too (see those classes' own comments).
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "metadata", columnDefinition = "jsonb")
	private String metadata;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@PrePersist
	void onCreate() {
		this.createdAt = Instant.now();
		if (this.occurredAt == null) {
			this.occurredAt = this.createdAt;
		}
	}

}
