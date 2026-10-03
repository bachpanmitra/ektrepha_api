package com.ektrepha.model;

import java.time.Instant;
import java.time.LocalDate;

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

/** One row per document/claim per nanny. No uniqueness constraint on (nanny, type) — a rejected submission can be resubmitted as a new row. */
@Entity
@Table(name = "nanny_verification")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NannyVerification {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "nanny_id", nullable = false)
	private Nanny nanny;

	@Column(name = "type", nullable = false)
	private VerificationDocType type;

	@Column(name = "s3_key", nullable = false, length = 500)
	private String s3Key;

	@Column(name = "status", nullable = false)
	private VerificationRecordStatus status;

	@Column(name = "vendor_reference_id", length = 100)
	private String vendorReferenceId;

	/** Only ever set for {@link VerificationDocType#BACKGROUND_CHECK} (the PCC) - {@code com.ektrepha.verification.impl.VerificationExpiryAuditJob} auto-suspends a nanny whose latest VERIFIED PCC has an expiry_date in the past. */
	@Column(name = "expiry_date")
	private LocalDate expiryDate;

	/** One-way hash from {@code KycVerificationProvider#verifyIdentity} - only ever set on an {@link VerificationDocType#ID_PROOF} row. Never the raw extracted ID number. */
	@Column(name = "id_doc_hash", length = 128)
	private String idDocHash;

	/** One-way hash from {@code KycVerificationProvider#verifyLiveness} - only ever set on a {@link VerificationDocType#LIVENESS_SELFIE} row. Never a reusable biometric template. */
	@Column(name = "face_embedding_hash", length = 128)
	private String faceEmbeddingHash;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "reviewed_by")
	private User reviewedBy;

	@Column(name = "reviewed_at")
	private Instant reviewedAt;

	@Column(name = "rejection_reason", length = 255)
	private String rejectionReason;

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
