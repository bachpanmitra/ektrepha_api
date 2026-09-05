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

@Entity
@Table(name = "nanny")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Nanny {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false, unique = true)
	private User user;

	@Column(name = "first_name", nullable = false, length = 100)
	private String firstName;

	@Column(name = "last_name", nullable = false, length = 100)
	private String lastName;

	@Column(name = "bio", length = 2000)
	private String bio;

	@Column(name = "profile_photo_s3_key", length = 500)
	private String profilePhotoS3Key;

	@Column(name = "education_level", length = 50)
	private String educationLevel;

	@Column(name = "years_experience")
	private Integer yearsExperience;

	@Column(name = "hourly_rate", precision = 10, scale = 2)
	private BigDecimal hourlyRate;

	/** Derived rollup, recomputed in the service layer whenever a nanny_verification row changes — never set directly from a request. */
	@Column(name = "overall_verification_status", nullable = false)
	private NannyVerificationStatus overallVerificationStatus;

	@Column(name = "meta_data", columnDefinition = "jsonb")
	private String metaData;

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
