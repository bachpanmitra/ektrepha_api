package com.ektrepha.model;

import java.time.Instant;
import java.util.List;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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

/**
 * The nanny's own rating/feedback on a completed stay (migration 057) - the reverse direction of
 * {@link Review} (parent rates nanny). One per booking, never edited after submission, same
 * reasoning as {@link Review} being a single current-state row.
 */
@Entity
@Table(name = "nanny_stay_review")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NannyStayReview {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "booking_id", nullable = false, unique = true)
	private Booking booking;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "nanny_id", nullable = false)
	private Nanny nanny;

	@Column(name = "stars", nullable = false)
	private Short stars;

	// ektrepha-nanny-ui's StayReviewTag values (RESPECTFUL/GAVE_ME_REST/...), stored as-is - the
	// app owns this vocabulary, same reasoning as Nanny#language not being an enum here.
	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(name = "tags", nullable = false)
	private List<String> tags;

	@Column(name = "private_note", length = 1000)
	private String privateNote;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@PrePersist
	void onCreate() {
		this.createdAt = Instant.now();
	}

}
