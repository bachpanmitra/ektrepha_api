package com.ektrepha.model;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "nanny")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Nanny {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false, unique = true)
	private User user;

	@Column(name = "first_name", nullable = false, length = 100)
	private String firstName;

	@Column(name = "last_name", nullable = false, length = 100)
	private String lastName;

	@Column(name = "bio", length = 2000)
	private String bio;

	@Column(name = "profile_photo_s3_key", length = 500)
	private String profilePhotoS3Key;

	@Column(name = "education_level", length = 50)
	private String educationLevel;

	@Column(name = "years_experience")
	private Integer yearsExperience;

	@Column(name = "hourly_rate", precision = 10, scale = 2)
	private BigDecimal hourlyRate;

	/**
	 * Derived rollup, recomputed by {@code NannyVerificationServiceImpl} whenever a
	 * nanny_verification row changes. No public setter — {@code @Setter(NONE)} here overrides the
	 * class-level {@code @Setter} so this field can only change via {@link #applyRecomputedVerificationStatus},
	 * not a stray {@code setOverallVerificationStatus} call from anywhere else in the codebase.
	 */
	@Setter(AccessLevel.NONE)
	@Column(name = "overall_verification_status", nullable = false)
	private NannyVerificationStatus overallVerificationStatus;

	/** The only way to change {@link #overallVerificationStatus} — called exclusively by the verification recompute logic. */
	public void applyRecomputedVerificationStatus(NannyVerificationStatus status) {
		this.overallVerificationStatus = status;
	}

	// @JdbcTypeCode(JSON) is required, not just columnDefinition — without it Hibernate binds this
	// String as VARCHAR, and Postgres rejects a VARCHAR value against a jsonb column with no
	// implicit cast ("column meta_data is of type jsonb but expression is of type character
	// varying"), even when the value is null. Found via NannyVerificationRecomputeTest — the first
	// real JPA write path for this entity; every prior manual test inserted nanny rows via raw SQL.
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "meta_data", columnDefinition = "jsonb")
	private String metaData;

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
