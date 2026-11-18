package com.ektrepha.banner.controller;

import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ektrepha.banner.dto.response.PublicBannerResponse;
import com.ektrepha.banner.service.BannerService;
import com.ektrepha.model.BannerCareType;

import lombok.RequiredArgsConstructor;

/** Public (anonymous) read of live banners for the home page carousel - see SecurityConfig's permitAll for this path. */
@RestController
@RequestMapping("/api/v1/home")
@RequiredArgsConstructor
public class HomeBannerController {

	private final BannerService bannerService;

	@GetMapping("/banners")
	public ResponseEntity<List<PublicBannerResponse>> getLiveBanners(@RequestParam(required = false) BannerCareType careType) {
		return ResponseEntity.ok()
				.header(HttpHeaders.CACHE_CONTROL, "public, max-age=60")
				.body(bannerService.getLiveBanners(careType));
	}

}
