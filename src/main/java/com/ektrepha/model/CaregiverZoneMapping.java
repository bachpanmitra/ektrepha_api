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
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A nanny's (caregiver's) coverage of one zone x service-type, and their own declared rate for the marketplace pricing model. {@code ownRate} is clamped to the zone's [rate_min, rate_max] at quote time, not at write time. */
@Entity
@Table(name = "caregiver_zone_mapping")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CaregiverZoneMapping {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "nanny_id", nullable = false)
	private Nanny caregiver;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "zone_area_id", nullable = false)
	private ZoneArea zoneArea;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "service_type_id", nullable = false)
	private ServiceType serviceType;

	@Column(name = "own_rate", precision = 10, scale = 2)
	private BigDecimal ownRate;

	@Column(name = "is_active", nullable = false)
	private boolean active;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@PrePersist
	void onCreate() {
		this.createdAt = Instant.now();
	}

}
