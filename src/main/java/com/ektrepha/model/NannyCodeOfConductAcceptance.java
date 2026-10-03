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

/** One signed acceptance of the code of conduct, at a specific version. Every acceptance is kept (not just the latest) - re-accepting after a version bump must not erase the record of accepting an earlier version on an earlier date. */
@Entity
@Table(name = "nanny_code_of_conduct_acceptance")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NannyCodeOfConductAcceptance {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "nanny_id", nullable = false)
	private Nanny nanny;

	@Column(name = "version", nullable = false, length = 20)
	private String version;

	@Column(name = "accepted_at", nullable = false)
	private Instant acceptedAt;

	@Column(name = "ip_address", length = 45)
	private String ipAddress;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@PrePersist
	void onCreate() {
		this.createdAt = Instant.now();
	}

}
