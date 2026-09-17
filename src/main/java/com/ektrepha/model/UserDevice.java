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
 * Maps {@code user_device} (migration 027) — a push token registered for this user's device. No
 * push provider reads this yet (PRD v2 §17 hard blocker); this is token storage only.
 * {@code platform} is a plain string ({@code "ios"}/{@code "android"}, lowercase), not a Java
 * enum — matches the DB's own {@code CHECK (platform IN ('ios','android'))} constraint directly
 * rather than via an unconventional lowercase-valued enum.
 */
@Entity
@Table(name = "user_device")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserDevice {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Column(name = "push_token", nullable = false, unique = true, length = 500)
	private String pushToken;

	@Column(name = "platform", nullable = false, length = 10)
	private String platform;

	@Column(name = "last_active_at")
	private Instant lastActiveAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@PrePersist
	void onCreate() {
		this.createdAt = Instant.now();
	}

}
