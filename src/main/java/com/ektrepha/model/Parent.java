package com.ektrepha.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.ektrepha.util.PublicIds;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "parent")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Parent {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** Opaque external id (e.g. par_1a4) — Care Manager feature only; every other endpoint still uses {@link #id}. */
	@Column(name = "public_id", nullable = false, unique = true, length = 20)
	private String publicId;

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false, unique = true)
	private User user;

	/** This parent's one fixed Care Manager — nullable until the backfill migration runs; NOT NULL in a later migration. Changes only via an Admin reassign/connect, never automatically. */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "care_manager_id")
	private CareManager careManager;

	// Nullable (migration 029) — an auto-vivified stub row (created on a first child/address, before
	// P1/P2 is ever completed) has no name yet.
	@Column(name = "first_name", length = 100)
	private String firstName;

	@Column(name = "last_name", length = 100)
	private String lastName;

	@Column(name = "profile_photo_s3_key", length = 500)
	private String profilePhotoS3Key;

	// Migration 034 - the address a returning parent's post-login "care location" resolves to,
	// distinct from is_primary (see CareLocationServiceImpl). NULL until ever explicitly confirmed.
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "last_selected_address_id")
	private ParentAddress lastSelectedAddress;

	// @JdbcTypeCode(JSON) required — see Nanny.metaData for why columnDefinition alone isn't enough.
	@JdbcTypeCode(SqlTypes.JSON)
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
		if (this.publicId == null) {
			this.publicId = PublicIds.of(PublicIds.PARENT_PREFIX);
		}
	}

	@PreUpdate
	void onUpdate() {
		this.updatedAt = Instant.now();
	}

}
