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

/**
 * The current, always-overwritten demand reading for one zone x service-type (unique constraint
 * enforces "latest only" - see {@link ZoneDemandSnapshotHistory} for the append-only trail).
 * Plain id columns rather than JPA relations - this table is only ever written by id and read by
 * id from {@code DemandSnapshotRecomputeJob} / the pricing hot path, never navigated as a graph.
 */
@Entity
@Table(name = "zone_demand_snapshot")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ZoneDemandSnapshot {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "zone_area_id", nullable = false)
	private Long zoneAreaId;

	@Column(name = "service_type_id", nullable = false)
	private Long serviceTypeId;

	@Column(name = "open_booking_requests", nullable = false)
	private Integer openBookingRequests;

	@Column(name = "available_caregivers", nullable = false)
	private Integer availableCaregivers;

	@Column(name = "demand_ratio", precision = 6, scale = 3)
	private BigDecimal demandRatio;

	@Column(name = "computed_multiplier", nullable = false, precision = 4, scale = 2)
	private BigDecimal computedMultiplier;

	@Column(name = "computed_at", nullable = false)
	private Instant computedAt;

}
