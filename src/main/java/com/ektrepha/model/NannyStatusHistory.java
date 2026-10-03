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

/** One row per {@code nanny.overall_verification_status} transition - the full audit trail a compliance review or incident investigation needs; {@code nanny.statusReason} only ever holds the *current* reason. {@code changedBy} is null for a scheduled-job transition (PCC expiry, periodic re-verification). */
@Entity
@Table(name = "nanny_status_history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NannyStatusHistory {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "nanny_id", nullable = false)
	private Nanny nanny;

	@Column(name = "previous_status", nullable = false)
	private NannyVerificationStatus previousStatus;

	@Column(name = "new_status", nullable = false)
	private NannyVerificationStatus newStatus;

	@Column(name = "reason", length = 500)
	private String reason;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "changed_by")
	private User changedBy;

	@Column(name = "changed_at", nullable = false, updatable = false)
	private Instant changedAt;

	@PrePersist
	void onCreate() {
		this.changedAt = Instant.now();
	}

}
