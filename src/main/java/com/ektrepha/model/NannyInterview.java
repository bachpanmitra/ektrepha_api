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

/** One scheduled/conducted video interview. No uniqueness constraint on nanny_id - a NEEDS_FOLLOWUP outcome gets a fresh row for the follow-up interview rather than overwriting this one. */
@Entity
@Table(name = "nanny_interview")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NannyInterview {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "nanny_id", nullable = false)
	private Nanny nanny;

	@Column(name = "scheduled_at", nullable = false)
	private Instant scheduledAt;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "conducted_by")
	private User conductedBy;

	@Column(name = "conducted_at")
	private Instant conductedAt;

	@Column(name = "outcome", nullable = false)
	@Builder.Default
	private InterviewOutcome outcome = InterviewOutcome.SCHEDULED;

	@Column(name = "notes", length = 1000)
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
