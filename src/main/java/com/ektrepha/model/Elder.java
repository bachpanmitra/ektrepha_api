package com.ektrepha.model;

import java.time.Instant;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A senior family member Elder Care (senior_care) is booked for. Mirrors {@link Children} - owned
 * via the {@link ParentElder} join table rather than a direct FK, so the same multi-guardian
 * sharing a child already supports works for an elder too. {@code removedAt} is a soft delete
 * (unlike {@code Children}, which has none) - set, never rows deleted outright.
 */
@Entity
@Table(name = "elders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Elder {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "first_name", nullable = false, length = 100)
	private String firstName;

	@Column(name = "last_name", length = 100)
	private String lastName;

	@Column(name = "age", nullable = false)
	private Integer age;

	@Column(name = "gender", length = 10)
	private String gender;

	@Column(name = "mobility", nullable = false)
	@Enumerated(EnumType.STRING)
	private ElderMobility mobility;

	@Builder.Default
	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(name = "conditions", columnDefinition = "text[]")
	private List<String> conditions = List.of();

	@Builder.Default
	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(name = "allergies", columnDefinition = "text[]")
	private List<String> allergies = List.of();

	@Column(name = "medication_notes", length = 500)
	private String medicationNotes;

	@Column(name = "preferred_language", length = 30)
	private String preferredLanguage;

	@Column(name = "emergency_contact_name", length = 100)
	private String emergencyContactName;

	@Column(name = "emergency_contact_phone", length = 20)
	private String emergencyContactPhone;

	@Column(name = "doctor_name", length = 100)
	private String doctorName;

	@Column(name = "doctor_phone", length = 20)
	private String doctorPhone;

	@Column(name = "profile_photo_s3_key", length = 500)
	private String profilePhotoS3Key;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "meta_data", columnDefinition = "jsonb")
	private String metaData;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "removed_at")
	private Instant removedAt;

	@PrePersist
	void onCreate() {
		this.createdAt = Instant.now();
	}

}
