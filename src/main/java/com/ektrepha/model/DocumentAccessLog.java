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

/** Audit trail (migration 048) for who viewed a nanny's verification document, and when. */
@Entity
@Table(name = "document_access_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentAccessLog {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "document_id", nullable = false)
	private NannyVerification document;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "viewed_by_user_id", nullable = false)
	private User viewedBy;

	@Column(name = "viewed_at", nullable = false)
	private Instant viewedAt;

	@PrePersist
	void onCreate() {
		this.viewedAt = Instant.now();
	}

}
