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
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One zone x service-type's price configuration - either a flat price or a min/max range. At most one row per (zone_area, service_type), enforced by a DB unique constraint. */
@Entity
@Table(name = "zone_service_pricing")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ZoneServicePricing {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "zone_area_id", nullable = false)
	private ZoneArea zoneArea;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "service_type_id", nullable = false)
	private ServiceType serviceType;

	@Column(name = "pricing_mode", nullable = false)
	private PricingMode pricingMode;

	@Column(name = "fix_price", precision = 10, scale = 2)
	private BigDecimal fixPrice;

	@Column(name = "rate_min", precision = 10, scale = 2)
	private BigDecimal rateMin;

	@Column(name = "rate_max", precision = 10, scale = 2)
	private BigDecimal rateMax;

	@Column(name = "unit_price", precision = 10, scale = 2)
	private BigDecimal unitPrice;

	@Column(name = "currency", nullable = false, length = 3)
	private String currency;

	@Column(name = "min_booking_hours", nullable = false, precision = 4, scale = 2)
	private BigDecimal minBookingHours;

	@Column(name = "platform_fee_pct", nullable = false, precision = 5, scale = 2)
	private BigDecimal platformFeePct;

	@Column(name = "is_active", nullable = false)
	private boolean active;

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
