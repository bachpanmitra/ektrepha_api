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
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One row per (nanny, {@link ConsentType}) the nanny has granted — re-granting updates {@code grantedAt} rather than inserting a second row. */
@Entity
@Table(name = "nanny_consent")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NannyConsent {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "nanny_id", nullable = false)
	private Nanny nanny;

	@Column(name = "type", nullable = false)
	private ConsentType type;

	@Column(name = "granted_at", nullable = false)
	private Instant grantedAt;

}
