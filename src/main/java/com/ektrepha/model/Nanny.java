package com.ektrepha.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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

	/** Nullable only for rows predating migration 38. Every new nanny must supply it - {@link #isAtLeast18} is the 18+ gate checked at submission time, and the DB also enforces it via a CHECK constraint. */
	@Column(name = "dob")
	private LocalDate dob;

	/** Most recently reported device id (mobile app login/install) - checked against {@code banned_identity.device_id} at document-submission time. */
	@Column(name = "device_id", length = 128)
	private String deviceId;

	/**
	 * Derived rollup, recomputed by {@code NannyVerificationServiceImpl} whenever a
	 * nanny_verification row changes. No public setter — {@code @Setter(NONE)} here overrides the
	 * class-level {@code @Setter} so this field can only change via {@link #applyRecomputedVerificationStatus}
	 * or {@link #applyManualStatusChange}, not a stray {@code setOverallVerificationStatus} call from
	 * anywhere else in the codebase.
	 */
	@Setter(AccessLevel.NONE)
	@Column(name = "overall_verification_status", nullable = false)
	private NannyVerificationStatus overallVerificationStatus;

	@Setter(AccessLevel.NONE)
	@Column(name = "status_reason", length = 500)
	private String statusReason;

	@Setter(AccessLevel.NONE)
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "status_changed_by")
	private User statusChangedBy;

	@Setter(AccessLevel.NONE)
	@Column(name = "status_changed_at")
	private Instant statusChangedAt;

	/**
	 * The automatic recompute path (document/reference/interview/training/code-of-conduct status
	 * changing) - deliberately a no-op when the nanny is currently {@code SUSPENDED} or
	 * {@code BANNED}, since those are admin/scheduled-job-only states (PRD: a suspended caregiver
	 * whose, say, EDUCATION doc gets approved must not silently pop back to APPROVED). Use
	 * {@link #applyManualStatusChange} for every transition that must actually take effect
	 * regardless of the current state (approve/reject/suspend/ban/reinstate).
	 */
	public void applyRecomputedVerificationStatus(NannyVerificationStatus status) {
		if (this.overallVerificationStatus == NannyVerificationStatus.SUSPENDED
				|| this.overallVerificationStatus == NannyVerificationStatus.BANNED) {
			return;
		}
		this.overallVerificationStatus = status;
	}

	/** Explicit admin/system transition (approve/reject/suspend/ban/reinstate) - always applies, unlike {@link #applyRecomputedVerificationStatus}. {@code changedBy} is null for a scheduled-job transition (e.g. PCC expiry auto-suspend). */
	public void applyManualStatusChange(NannyVerificationStatus status, String reason, User changedBy) {
		this.overallVerificationStatus = status;
		this.statusReason = reason;
		this.statusChangedBy = changedBy;
		this.statusChangedAt = Instant.now();
	}

	/** Age-18 gate for KYC (PRD: "Age 18+ enforced at signup and in KYC"). False (not true) when {@link #dob} isn't set yet - a nanny with no DOB on file can never pass the gate. */
	public boolean isAtLeast18() {
		return dob != null && Period.between(dob, LocalDate.now()).getYears() >= 18;
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
