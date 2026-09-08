package com.ektrepha.model;

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

/**
 * A nanny's declared coverage circle (center + radius). PRD "Nanny Proximity Search" scopes this
 * to one center point per nanny for now (no schema-level uniqueness on nanny_id yet, but the
 * service layer upserts rather than creates a second row).
 */
@Entity
@Table(name = "nanny_service_area")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NannyServiceArea {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "nanny_id", nullable = false)
	private Nanny nanny;

	@Column(name = "lat", nullable = false)
	private Double lat;

	@Column(name = "lng", nullable = false)
	private Double lng;

	@Column(name = "radius_km", nullable = false)
	private Integer radiusKm;

}
