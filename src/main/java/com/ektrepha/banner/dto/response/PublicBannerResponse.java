package com.ektrepha.banner.dto.response;

import java.util.UUID;

import com.ektrepha.model.BannerLinkType;

/** No admin-only fields (status, timestamps, care_type) — only what the home page carousel needs. */
public record PublicBannerResponse(
		UUID id,
		String title,
		String ctaText,
		String imageMobileUrl,
		String imageDesktopUrl,
		BannerLinkType linkType,
		String linkTarget) {
}
