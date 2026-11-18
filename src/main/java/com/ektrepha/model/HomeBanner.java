package com.ektrepha.model;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** PRD: Admin Home Page Banner Management (Phase 1). status is never a column — see BannerStatus. */
@Entity
@Table(name = "home_banners")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HomeBanner {

	@Id
	private UUID id;

	@Column(name = "title", nullable = false, length = 60)
	private String title;

	@Column(name = "cta_text", length = 30)
	private String ctaText;

	@Column(name = "image_mobile_url", nullable = false)
	private String imageMobileUrl;

	/** Falls back to imageMobileUrl on the home page when unset — see HomeBannerMapper. */
	@Column(name = "image_desktop_url")
	private String imageDesktopUrl;

	@Enumerated(EnumType.STRING)
	@Column(name = "link_type", nullable = false, length = 20)
	private BannerLinkType linkType;

	@Column(name = "link_target")
	private String linkTarget;

	@Enumerated(EnumType.STRING)
	@Column(name = "care_type", nullable = false, length = 20)
	private BannerCareType careType;

	@Column(name = "start_at", nullable = false)
	private Instant startAt;

	@Column(name = "end_at")
	private Instant endAt;

	@Column(name = "sort_order", nullable = false)
	private int sortOrder;

	@Column(name = "is_active", nullable = false)
	private boolean active;

	/** Null until the first publish — see computeStatus. */
	@Column(name = "published_at")
	private Instant publishedAt;

	@Column(name = "created_by", nullable = false)
	private Long createdBy;

	@Column(name = "updated_by", nullable = false)
	private Long updatedBy;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Column(name = "deleted_at")
	private Instant deletedAt;

	@PrePersist
	void onCreate() {
		if (id == null) {
			id = UUID.randomUUID();
		}
		Instant now = Instant.now();
		this.createdAt = now;
		this.updatedAt = now;
	}

	@PreUpdate
	void onUpdate() {
		this.updatedAt = Instant.now();
	}

	/** DRAFT/SCHEDULED/LIVE/EXPIRED/INACTIVE — computed, never stored (see BannerStatus). */
	public BannerStatus computeStatus(Instant now) {
		if (!active) {
			return publishedAt == null ? BannerStatus.DRAFT : BannerStatus.INACTIVE;
		}
		if (endAt != null && now.isAfter(endAt)) {
			return BannerStatus.EXPIRED;
		}
		if (now.isBefore(startAt)) {
			return BannerStatus.SCHEDULED;
		}
		return BannerStatus.LIVE;
	}

}
