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
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** An in-person induction/refresher session HR schedules for a nanny. */
@Entity
@Table(name = "nanny_training_session")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NannyTrainingSession {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "nanny_id", nullable = false)
	private Nanny nanny;

	@Column(name = "title", nullable = false, length = 150)
	private String title;

	@Column(name = "start_date", nullable = false)
	private LocalDate startDate;

	@Column(name = "location", nullable = false, length = 200)
	private String location;

	@Column(name = "duration_days", nullable = false)
	private int durationDays;

	@Column(name = "completed", nullable = false)
	private boolean completed;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@PrePersist
	void onCreate() {
		this.createdAt = Instant.now();
	}

}
