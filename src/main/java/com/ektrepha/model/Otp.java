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

@Entity
@Table(name = "otps")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Otp {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** Null pre-signup, before the user row exists (phone signup flow). */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id")
	private User user;

	@Column(name = "phone_or_email", nullable = false)
	private String phoneOrEmail;

	/** Bcrypt hash of the OTP code — never stored in plaintext. */
	@Column(nullable = false)
	private String otp;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private OtpPurpose purpose;

	@Builder.Default
	@Column(name = "attempt_count", nullable = false)
	private int attemptCount = 0;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@Builder.Default
	@Column(name = "is_used", nullable = false)
	private boolean used = false;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@PrePersist
	void onCreate() {
		this.createdAt = Instant.now();
	}

}
