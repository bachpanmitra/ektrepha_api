package com.ektrepha.model;

import java.math.BigDecimal;
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

/** Ranking weights are tunable without a code deploy — history is preserved via {@code activeFrom} for auditing. */
@Entity
@Table(name = "ranking_config")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RankingConfig {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "factor", nullable = false)
	private RankingFactor factor;

	@Column(name = "weight", nullable = false, precision = 4, scale = 3)
	private BigDecimal weight;

	@Column(name = "active_from", nullable = false)
	private Instant activeFrom;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "created_by")
	private User createdBy;

}
