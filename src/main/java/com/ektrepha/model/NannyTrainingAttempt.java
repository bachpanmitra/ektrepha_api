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

/** One quiz submission for the mandatory child-safety training module. Every attempt is kept (not just the latest) - a failed attempt followed by a pass is part of the record, not something to overwrite. */
@Entity
@Table(name = "nanny_training_attempt")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NannyTrainingAttempt {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "nanny_id", nullable = false)
	private Nanny nanny;

	@Column(name = "module_version", nullable = false, length = 20)
	private String moduleVersion;

	@Column(name = "score", nullable = false)
	private int score;

	@Column(name = "passed", nullable = false)
	private boolean passed;

	@Column(name = "started_at", nullable = false)
	private Instant startedAt;

	@Column(name = "completed_at", nullable = false)
	private Instant completedAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@PrePersist
	void onCreate() {
		this.createdAt = Instant.now();
	}

}
