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

import com.ektrepha.util.PublicIds;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "complaints")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Complaint {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "public_id", nullable = false, unique = true, length = 20)
	private String publicId;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "parent_id", nullable = false)
	private Parent parent;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "nanny_id")
	private Nanny nanny;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "booking_id")
	private Booking booking;

	/** Owner of the complaint until it's closed — the parent's CM at the time it was raised. */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "care_manager_id", nullable = false)
	private CareManager careManager;

	@Enumerated(EnumType.STRING)
	@Column(name = "category", nullable = false, length = 20)
	private ComplaintCategory category;

	@Column(name = "description", nullable = false)
	private String description;

	@Builder.Default
	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 15)
	private ComplaintStatus status = ComplaintStatus.OPEN;

	/** The senior manager a SAFETY complaint escalates to, set in the same transaction it's raised. */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "escalated_to")
	private User escalatedTo;

	@Column(name = "escalated_at")
	private Instant escalatedAt;

	@Builder.Default
	@Column(name = "trial_refund_issued", nullable = false)
	private boolean trialRefundIssued = false;

	@Column(name = "resolution")
	private String resolution;

	@Column(name = "raised_at", nullable = false)
	private Instant raisedAt;

	@Column(name = "closed_at")
	private Instant closedAt;

	@PrePersist
	void onCreate() {
		if (this.raisedAt == null) {
			this.raisedAt = Instant.now();
		}
		if (this.publicId == null) {
			this.publicId = PublicIds.of(PublicIds.COMPLAINT_PREFIX);
		}
	}

}
