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

/** The nanny'\''s private rating of a family after a shift - never shown to the parent. tags is a comma-separated list of FamilyReviewTag names. */
@Entity
@Table(name = "nanny_family_review")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NannyFamilyReview {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "booking_id", nullable = false, unique = true)
	private Booking booking;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "nanny_id", nullable = false)
	private Nanny nanny;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "parent_id", nullable = false)
	private Parent parent;

	@Column(name = "stars", nullable = false)
	private short stars;

	@Column(name = "tags", length = 200)
	private String tags;

	@Column(name = "private_note", length = 1000)
	private String privateNote;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@PrePersist
	void onCreate() {
		this.createdAt = Instant.now();
	}

}
