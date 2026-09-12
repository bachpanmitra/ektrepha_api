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
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Per zone-service demand-surge tuning. {@code isEnabled=false} is the safe default (design doc's rollout decision #5) - a new zone gets no dynamic surge until an operator explicitly turns it on. */
@Entity
@Table(name = "dynamic_pricing_config")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DynamicPricingConfig {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "zone_service_pricing_id", nullable = false, unique = true)
	private ZoneServicePricing zoneServicePricing;

	@Column(name = "is_enabled", nullable = false)
	private boolean enabled;

	@Column(name = "demand_threshold_low", nullable = false, precision = 4, scale = 2)
	private BigDecimal demandThresholdLow;

	@Column(name = "demand_threshold_high", nullable = false, precision = 4, scale = 2)
	private BigDecimal demandThresholdHigh;

	@Column(name = "min_multiplier", nullable = false, precision = 4, scale = 2)
	private BigDecimal minMultiplier;

	@Column(name = "max_multiplier", nullable = false, precision = 4, scale = 2)
	private BigDecimal maxMultiplier;

	@Column(name = "recompute_interval_mins", nullable = false)
	private Integer recomputeIntervalMins;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@PrePersist
	void onCreate() {
		Instant now = Instant.now();
		this.createdAt = now;
		this.updatedAt = now;
	}

	@PreUpdate
	void onUpdate() {
		this.updatedAt = Instant.now();
	}

}
