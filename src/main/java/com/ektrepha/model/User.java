package com.ektrepha.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private String name;

	@Column(unique = true)
	private String email;

	@Column(unique = true)
	private String phone;

	/** Always a bcrypt hash, never plaintext — null for Google-only users until they set one. */
	private String password;

	@Column(name = "google_id", unique = true)
	private String googleId;

	@Builder.Default
	@Column(name = "is_active", nullable = false)
	private boolean active = true;

	@Builder.Default
	@Column(name = "email_verified", nullable = false)
	private boolean emailVerified = false;

	@Builder.Default
	@Column(name = "phone_verified", nullable = false)
	private boolean phoneVerified = false;

	@Enumerated(EnumType.STRING)
	@Column(name = "user_source", nullable = false, length = 20)
	private UserSource userSource;

	@Enumerated(EnumType.STRING)
	@Column(name = "user_type", nullable = false, length = 20)
	private UserType userType;

	// @Builder.Default matters here: every existing User.builder() call across the signup flows
	// never sets this explicitly, and the column is NOT NULL — without a default, those inserts
	// would break.
	@Builder.Default
	@Column(name = "status", nullable = false)
	private UserStatus status = UserStatus.ACTIVE;

	/** Set on soft-delete (A6) alongside {@link #status} — never a hard delete, booking/financial history must survive. */
	@Column(name = "deleted_at")
	private Instant deletedAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@PrePersist
	void onCreate() {
		Instant now = Instant.now();
		this.createdAt = now;
		this.updatedAt = now;
	}

	@PreUpdate
	void onUpdate() {
		this.updatedAt = Instant.now();
	}

}
