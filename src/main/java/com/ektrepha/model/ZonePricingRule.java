package com.ektrepha.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;

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

/** A day-type/time-window price adjustment (surge or discount) for one {@link ZoneServicePricing}. Highest {@code priority} wins on an overlapping match. */
@Entity
@Table(name = "zone_pricing_rules")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ZonePricingRule {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "zone_service_pricing_id", nullable = false)
	private ZoneServicePricing zoneServicePricing;

	@Column(name = "day_type", nullable = false)
	private DayType dayType;

	@Column(name = "start_time", nullable = false)
	private LocalTime startTime;

	@Column(name = "end_time", nullable = false)
	private LocalTime endTime;

	@Column(name = "price_multiplier", nullable = false, precision = 4, scale = 2)
	private BigDecimal priceMultiplier;

	@Column(name = "adjusted_fix_price", precision = 10, scale = 2)
	private BigDecimal adjustedFixPrice;

	@Column(name = "priority", nullable = false)
	private Integer priority;

	@Column(name = "is_active", nullable = false)
	private boolean active;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@PrePersist
	void onCreate() {
		this.createdAt = Instant.now();
	}

}
