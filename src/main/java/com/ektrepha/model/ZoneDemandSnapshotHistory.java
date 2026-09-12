package com.ektrepha.model;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Append-only history of every demand recompute, for tuning thresholds later - never read on the pricing hot path. */
@Entity
@Table(name = "zone_demand_snapshot_history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ZoneDemandSnapshotHistory {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "zone_area_id", nullable = false)
	private Long zoneAreaId;

	@Column(name = "service_type_id", nullable = false)
	private Long serviceTypeId;

	@Column(name = "demand_ratio", precision = 6, scale = 3)
	private BigDecimal demandRatio;

	@Column(name = "computed_multiplier", precision = 4, scale = 2)
	private BigDecimal computedMultiplier;

	@Column(name = "computed_at", nullable = false)
	private Instant computedAt;

}
