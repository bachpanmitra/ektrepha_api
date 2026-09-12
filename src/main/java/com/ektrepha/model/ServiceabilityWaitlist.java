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

/** Demand capture for a not-yet-live pincode/service-type combo. Plain id columns (not JPA relations) since this is matched by id, not navigated, by {@link com.ektrepha.serviceability.event.WaitlistNotificationListener}. */
@Entity
@Table(name = "serviceability_waitlist")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ServiceabilityWaitlist {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "pincode", length = 6)
	private String pincode;

	@Column(name = "zone_area_id")
	private Long zoneAreaId;

	@Column(name = "service_type_id")
	private Long serviceTypeId;

	@Column(name = "contact", nullable = false)
	private String contact;

	@Column(name = "notified_at")
	private Instant notifiedAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@PrePersist
	void onCreate() {
		this.createdAt = Instant.now();
	}

}
