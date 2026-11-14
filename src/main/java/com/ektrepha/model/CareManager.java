package com.ektrepha.model;

import java.time.Instant;
import java.time.LocalTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import com.ektrepha.util.PublicIds;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A senior Ektrepha employee who manages a fixed set of families' nannies. Backed by a
 * {@code users} row with {@link UserType#CARE_MANAGER} — there is no separate admin_users table in
 * this codebase, so CM login reuses the existing email/password flow (AuthServiceImpl), same as
 * ADMIN.
 */
@Entity
@Table(name = "care_managers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CareManager {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "public_id", nullable = false, unique = true, length = 20)
	private String publicId;

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false, unique = true)
	private User user;

	@Column(name = "name", nullable = false, length = 100)
	private String name;

	@Column(name = "phone", nullable = false, unique = true, length = 15)
	private String phone;

	@Column(name = "email", nullable = false, unique = true, length = 255)
	private String email;

	@Column(name = "photo_url")
	private String photoUrl;

	@Enumerated(EnumType.STRING)
	@Column(name = "area", nullable = false, length = 20)
	private CmArea area;

	@Builder.Default
	@Column(name = "active", nullable = false)
	private boolean active = true;

	@Builder.Default
	@Column(name = "max_parents", nullable = false)
	private short maxParents = 150;

	@Builder.Default
	@Column(name = "auto_assign_new_signups", nullable = false)
	private boolean autoAssignNewSignups = true;

	@Column(name = "shift_start", nullable = false)
	private LocalTime shiftStart;

	@Column(name = "shift_end", nullable = false)
	private LocalTime shiftEnd;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@PrePersist
	void onCreate() {
		Instant now = Instant.now();
		this.createdAt = now;
		this.updatedAt = now;
		if (this.publicId == null) {
			this.publicId = PublicIds.of(PublicIds.CARE_MANAGER_PREFIX);
		}
	}

	@PreUpdate
	void onUpdate() {
		this.updatedAt = Instant.now();
	}

}
