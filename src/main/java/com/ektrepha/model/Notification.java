package com.ektrepha.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * One row in a user's in-app notification inbox (migration 057) - backs the nanny app's Updates
 * screen (GET/mark-read). {@code route}/{@code bookingId} are the deep-link the app's
 * NotificationDeepLink needs to jump to the right screen on tap.
 * <p>
 * Nothing writes to this table yet - every existing notifier (Care Manager, trial, SOS, workforce
 * requests) is push-only/fire-and-forget today. Wiring them to also insert here is a deliberate
 * follow-up, not bundled into this table's introduction.
 */
@Entity
@Table(name = "notification")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Column(name = "title", nullable = false, length = 200)
	private String title;

	@Column(name = "body", length = 500)
	private String body;

	// A RouteName the app's navigation.ts understands - kept as a bare string on this side too, so
	// the backend doesn't need to know the app's route list (same reasoning as the app's own
	// NotificationDeepLink type).
	@Column(name = "route", length = 50)
	private String route;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "booking_id")
	private Booking booking;

	@Column(name = "read_at")
	private Instant readAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@PrePersist
	void onCreate() {
		this.createdAt = Instant.now();
	}

}
