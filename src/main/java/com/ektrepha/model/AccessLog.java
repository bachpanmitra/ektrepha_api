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
 * Append-only audit trail: one row per detail read (VIEW) or write (EDIT) of a parent/nanny record
 * by a Care Manager or Admin (migration 053 revokes UPDATE/DELETE at the DB level where the
 * runtime role allows it; AccessLogRepository itself exposes no update/delete methods either way).
 * {@link #recordId} is the parent/nanny's internal {@code id}, not its public id — polymorphic, so
 * it can't be a real FK.
 */
@Entity
@Table(name = "access_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccessLog {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "actor_user_id", nullable = false)
	private User actor;

	@Enumerated(EnumType.STRING)
	@Column(name = "record_type", nullable = false, length = 10)
	private AccessLogRecordType recordType;

	@Column(name = "record_id", nullable = false)
	private Long recordId;

	@Enumerated(EnumType.STRING)
	@Column(name = "action", nullable = false, length = 10)
	private AccessLogAction action;

	@Column(name = "section", length = 30)
	private String section;

	@Column(name = "at", nullable = false)
	private Instant at;

	@PrePersist
	void onCreate() {
		if (this.at == null) {
			this.at = Instant.now();
		}
	}

}
