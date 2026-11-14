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

/**
 * One open (unassigned_at IS NULL) row per parent and per nanny at all times — enforced by the
 * partial unique indexes uq_cm_assignments_open_parent/open_nanny (migration 053), always written
 * in the same transaction as the parent.care_manager_id / nanny.care_manager_id update it mirrors.
 * Exactly one of {@link #parent} / {@link #nanny} is set (chk_cm_assignments_parent_xor_nanny).
 */
@Entity
@Table(name = "cm_assignments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CmAssignment {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "parent_id")
	private Parent parent;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "nanny_id")
	private Nanny nanny;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "care_manager_id", nullable = false)
	private CareManager careManager;

	@Column(name = "assigned_at", nullable = false)
	private Instant assignedAt;

	@Column(name = "unassigned_at")
	private Instant unassignedAt;

	/** Null = system (auto-assignment at signup); set = the admin who performed a reassign/connect. */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "assigned_by")
	private User assignedBy;

	@Enumerated(EnumType.STRING)
	@Column(name = "reason", nullable = false, length = 20)
	private CmAssignmentReason reason;

	@Column(name = "parent_notified_at")
	private Instant parentNotifiedAt;

	@PrePersist
	void onCreate() {
		if (this.assignedAt == null) {
			this.assignedAt = Instant.now();
		}
	}

}
