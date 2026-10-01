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

/** Freeform safety/incident report, optionally tied to a booking/nanny. {@code reportedBy} can be a NANNY or PARENT account. */
@Entity
@Table(name = "incident_report")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IncidentReport {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "booking_id")
	private Booking booking;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "nanny_id")
	private Nanny nanny;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "reported_by", nullable = false)
	private User reportedBy;

	@Column(name = "description", nullable = false, length = 2000)
	private String description;

	@Column(name = "status", nullable = false)
	@Builder.Default
	private IncidentStatus status = IncidentStatus.OPEN;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "resolved_by")
	private User resolvedBy;

	@Column(name = "resolved_at")
	private Instant resolvedAt;

	@Column(name = "resolution_notes", length = 1000)
	private String resolutionNotes;

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
