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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One personal/professional reference a nanny has submitted for contact. The spec requires a
 * minimum of 2, each independently verified - unlike {@code nanny_verification}'s REFERENCE
 * type (a single document slot), there's no uniqueness constraint here, so a nanny can have any
 * number of these and the rollup counts how many are {@link VerificationRecordStatus#VERIFIED}.
 */
@Entity
@Table(name = "nanny_reference")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NannyReference {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "nanny_id", nullable = false)
	private Nanny nanny;

	@Column(name = "name", nullable = false, length = 150)
	private String name;

	@Column(name = "phone", nullable = false, length = 20)
	private String phone;

	@Column(name = "relationship", length = 100)
	private String relationship;

	@Column(name = "status", nullable = false)
	@Builder.Default
	private VerificationRecordStatus status = VerificationRecordStatus.PENDING;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "verified_by")
	private User verifiedBy;

	@Column(name = "verified_at")
	private Instant verifiedAt;

	@Column(name = "rejection_reason", length = 255)
	private String rejectionReason;

	@Column(name = "notes", length = 500)
	private String notes;

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
