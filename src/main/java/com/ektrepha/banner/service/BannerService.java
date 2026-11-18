package com.ektrepha.banner.service;

import java.util.List;
import java.util.UUID;

import com.ektrepha.banner.dto.request.CreateBannerRequest;
import com.ektrepha.banner.dto.request.ReorderBannersRequest;
import com.ektrepha.banner.dto.request.UpdateBannerRequest;
import com.ektrepha.banner.dto.response.AdminBannerResponse;
import com.ektrepha.banner.dto.response.BannerImageUploadResponse;
import com.ektrepha.banner.dto.response.PublicBannerResponse;
import com.ektrepha.model.BannerCareType;
import com.ektrepha.model.BannerStatus;

public interface BannerService {

	List<AdminBannerResponse> list(BannerStatus statusFilter);

	AdminBannerResponse get(UUID id);

	AdminBannerResponse create(CreateBannerRequest request, Long actorUserId);

	AdminBannerResponse update(UUID id, UpdateBannerRequest request, Long actorUserId);

	AdminBannerResponse setActive(UUID id, boolean active, Long actorUserId);

	AdminBannerResponse duplicate(UUID id, Long actorUserId);

	void reorder(ReorderBannersRequest request);

	void softDelete(UUID id);

	BannerImageUploadResponse uploadImage(byte[] content, String originalFilename, String contentType);

	/** careType null = no filter (every live banner) - see HomeBannerRepository.findAllLive. */
	List<PublicBannerResponse> getLiveBanners(BannerCareType careType);

}
