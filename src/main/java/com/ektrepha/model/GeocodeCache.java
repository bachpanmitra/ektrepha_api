package com.ektrepha.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Cache-aside store for free-text geocoding results, keyed by a normalized query, so repeat searches for the same text never re-hit the {@link com.ektrepha.serviceability.service.GeocodingProvider}. */
@Entity
@Table(name = "geocode_cache")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GeocodeCache {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "query_text", nullable = false)
	private String queryText;

	@Column(name = "normalized_query", nullable = false, unique = true)
	private String normalizedQuery;

	@Column(name = "lat")
	private Double lat;

	@Column(name = "lng")
	private Double lng;

	@Column(name = "formatted_address", length = 500)
	private String formattedAddress;

	@Column(name = "provider", length = 30)
	private String provider;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@PrePersist
	void onCreate() {
		this.createdAt = Instant.now();
	}

}
