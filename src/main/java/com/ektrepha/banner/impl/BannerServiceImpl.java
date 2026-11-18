package com.ektrepha.banner.impl;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ektrepha.banner.dto.request.CreateBannerRequest;
import com.ektrepha.banner.dto.request.ReorderBannersRequest;
import com.ektrepha.banner.dto.request.UpdateBannerRequest;
import com.ektrepha.banner.dto.response.AdminBannerResponse;
import com.ektrepha.banner.dto.response.BannerImageUploadResponse;
import com.ektrepha.banner.dto.response.PublicBannerResponse;
import com.ektrepha.banner.service.BannerService;
import com.ektrepha.exception.BannerNotFoundException;
import com.ektrepha.model.BannerCareType;
import com.ektrepha.model.BannerStatus;
import com.ektrepha.model.HomeBanner;
import com.ektrepha.repository.HomeBannerRepository;
import com.ektrepha.storage.GcsPhotoUrlService;

import lombok.RequiredArgsConstructor;

/** PRD: Admin Home Page Banner Management — Phase 1. */
@Service
@RequiredArgsConstructor
public class BannerServiceImpl implements BannerService {

	/** PRD edge case: "more than 5 live banners -> show top 5 by order". */
	private static final int MAX_LIVE_BANNERS = 5;

	private final HomeBannerRepository homeBannerRepository;
	private final GcsPhotoUrlService gcsPhotoUrlService;
	private final Clock clock;

	@Override
	@Transactional(readOnly = true)
	public List<AdminBannerResponse> list(BannerStatus statusFilter) {
		Instant now = Instant.now(clock);
		return homeBannerRepository.findByDeletedAtIsNullOrderBySortOrderAsc().stream()
				.map(b -> toAdminResponse(b, now))
				.filter(b -> statusFilter == null || b.status() == statusFilter)
				.toList();
	}

	@Override
	@Transactional(readOnly = true)
	public AdminBannerResponse get(UUID id) {
		return toAdminResponse(findOrThrow(id), Instant.now(clock));
	}

	@Override
	@Transactional
	public AdminBannerResponse create(CreateBannerRequest request, Long actorUserId) {
		Instant now = Instant.now(clock);
		HomeBanner banner = HomeBanner.builder()
				.title(request.title())
				.ctaText(request.ctaText())
				.imageMobileUrl(request.imageMobileKey())
				.imageDesktopUrl(request.imageDesktopKey())
				.linkType(request.linkType())
				.linkTarget(request.linkTarget())
				.careType(request.careType())
				.startAt(request.startAt())
				.endAt(request.endAt())
				// New banners land at the back of the order, same as a new item appended to any list.
				.sortOrder(nextSortOrder())
				.active(false)
				.createdBy(actorUserId)
				.updatedBy(actorUserId)
				.build();
		return toAdminResponse(homeBannerRepository.save(banner), now);
	}

	@Override
	@Transactional
	public AdminBannerResponse update(UUID id, UpdateBannerRequest request, Long actorUserId) {
		HomeBanner banner = findOrThrow(id);
		banner.setTitle(request.title());
		banner.setCtaText(request.ctaText());
		banner.setImageMobileUrl(request.imageMobileKey());
		banner.setImageDesktopUrl(request.imageDesktopKey());
		banner.setLinkType(request.linkType());
		banner.setLinkTarget(request.linkTarget());
		banner.setCareType(request.careType());
		banner.setStartAt(request.startAt());
		banner.setEndAt(request.endAt());
		banner.setUpdatedBy(actorUserId);
		return toAdminResponse(homeBannerRepository.save(banner), Instant.now(clock));
	}

	@Override
	@Transactional
	public AdminBannerResponse setActive(UUID id, boolean active, Long actorUserId) {
		HomeBanner banner = findOrThrow(id);
		banner.setActive(active);
		if (active && banner.getPublishedAt() == null) {
			banner.setPublishedAt(Instant.now(clock));
		}
		banner.setUpdatedBy(actorUserId);
		return toAdminResponse(homeBannerRepository.save(banner), Instant.now(clock));
	}

	@Override
	@Transactional
	public AdminBannerResponse duplicate(UUID id, Long actorUserId) {
		HomeBanner source = findOrThrow(id);
		HomeBanner copy = HomeBanner.builder()
				.title(source.getTitle() + " (copy)")
				.ctaText(source.getCtaText())
				.imageMobileUrl(source.getImageMobileUrl())
				.imageDesktopUrl(source.getImageDesktopUrl())
				.linkType(source.getLinkType())
				.linkTarget(source.getLinkTarget())
				.careType(source.getCareType())
				.startAt(source.getStartAt())
				.endAt(source.getEndAt())
				.sortOrder(nextSortOrder())
				// A duplicate is always a fresh Draft, never inherits the source's live/published state.
				.active(false)
				.createdBy(actorUserId)
				.updatedBy(actorUserId)
				.build();
		return toAdminResponse(homeBannerRepository.save(copy), Instant.now(clock));
	}

	@Override
	@Transactional
	public void reorder(ReorderBannersRequest request) {
		Map<UUID, HomeBanner> byId = homeBannerRepository.findAllById(request.orderedIds()).stream()
				.collect(Collectors.toMap(HomeBanner::getId, b -> b));
		int order = 0;
		for (UUID id : request.orderedIds()) {
			HomeBanner banner = byId.get(id);
			if (banner == null) {
				throw new BannerNotFoundException("No banner " + id);
			}
			banner.setSortOrder(order++);
		}
		homeBannerRepository.saveAll(byId.values());
	}

	@Override
	@Transactional
	public void softDelete(UUID id) {
		HomeBanner banner = findOrThrow(id);
		banner.setDeletedAt(Instant.now(clock));
		banner.setActive(false);
		homeBannerRepository.save(banner);
	}

	@Override
	public BannerImageUploadResponse uploadImage(byte[] content, String originalFilename, String contentType) {
		String extension = contentType.equals("image/png") ? "png" : contentType.equals("image/webp") ? "webp" : "jpg";
		String key = "home-banners/" + UUID.randomUUID() + "." + extension;
		gcsPhotoUrlService.upload(key, content, contentType);
		return new BannerImageUploadResponse(key, gcsPhotoUrlService.presign(key));
	}

	@Override
	@Transactional(readOnly = true)
	public List<PublicBannerResponse> getLiveBanners(BannerCareType careType) {
		Instant now = Instant.now(clock);
		List<HomeBanner> live = careType == null
				? homeBannerRepository.findAllLive(now)
				: homeBannerRepository.findLiveByCareType(careType, now);
		return live.stream().limit(MAX_LIVE_BANNERS).map(this::toPublicResponse).toList();
	}

	private int nextSortOrder() {
		return homeBannerRepository.findByDeletedAtIsNullOrderBySortOrderAsc().stream()
				.mapToInt(HomeBanner::getSortOrder)
				.max()
				.orElse(-1) + 1;
	}

	private HomeBanner findOrThrow(UUID id) {
		return homeBannerRepository.findById(id)
				.filter(b -> b.getDeletedAt() == null)
				.orElseThrow(() -> new BannerNotFoundException("No banner " + id));
	}

	private AdminBannerResponse toAdminResponse(HomeBanner b, Instant now) {
		return new AdminBannerResponse(b.getId(), b.getTitle(), b.getCtaText(),
				gcsPhotoUrlService.presign(b.getImageMobileUrl()), gcsPhotoUrlService.presign(b.getImageDesktopUrl()),
				b.getImageMobileUrl(), b.getImageDesktopUrl(),
				b.getLinkType(), b.getLinkTarget(), b.getCareType(), b.getStartAt(), b.getEndAt(), b.getSortOrder(), b.isActive(),
				b.computeStatus(now), b.getCreatedAt(), b.getUpdatedAt());
	}

	private PublicBannerResponse toPublicResponse(HomeBanner b) {
		return new PublicBannerResponse(b.getId(), b.getTitle(), b.getCtaText(),
				gcsPhotoUrlService.presign(b.getImageMobileUrl()), gcsPhotoUrlService.presign(b.getImageDesktopUrl()),
				b.getLinkType(), b.getLinkTarget());
	}

}
