package com.ektrepha.banner.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.ektrepha.model.BannerCareType;
import com.ektrepha.model.BannerLinkType;
import com.ektrepha.model.BannerStatus;

/**
 * List row and detail view share this shape (Phase 1 has no fields that differ between them).
 *
 * <p>{@code imageMobileUrl}/{@code imageDesktopUrl} are freshly presigned, display-only URLs
 * (the bucket is private); {@code imageMobileKey}/{@code imageDesktopKey} are the bare GCS object
 * keys the edit form must send back unchanged on update if the admin doesn't replace the image.
 */
public record AdminBannerResponse(
		UUID id,
		String title,
		String ctaText,
		String imageMobileUrl,
		String imageDesktopUrl,
		String imageMobileKey,
		String imageDesktopKey,
		BannerLinkType linkType,
		String linkTarget,
		BannerCareType careType,
		Instant startAt,
		Instant endAt,
		int sortOrder,
		boolean active,
		BannerStatus status,
		Instant createdAt,
		Instant updatedAt) {
}
