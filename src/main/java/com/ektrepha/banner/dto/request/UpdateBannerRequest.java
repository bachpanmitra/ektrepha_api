package com.ektrepha.banner.dto.request;

import java.time.Instant;

import com.ektrepha.model.BannerCareType;
import com.ektrepha.model.BannerLinkType;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Full-replace update (Phase 1) — same shape as create; active/sortOrder have their own endpoints. */
public record UpdateBannerRequest(
		@NotBlank @Size(max = 60) String title,
		@Size(max = 30) String ctaText,
		@NotBlank String imageMobileKey,
		String imageDesktopKey,
		@NotNull BannerLinkType linkType,
		String linkTarget,
		@NotNull BannerCareType careType,
		@NotNull Instant startAt,
		Instant endAt) {

	@AssertTrue(message = "End date must be after the start date")
	public boolean isEndAfterStart() {
		return endAt == null || startAt == null || endAt.isAfter(startAt);
	}

	@AssertTrue(message = "A link target is required unless Link type is None")
	public boolean isLinkTargetPresentWhenNeeded() {
		return linkType == null || linkType == BannerLinkType.NONE || (linkTarget != null && !linkTarget.isBlank());
	}

}
