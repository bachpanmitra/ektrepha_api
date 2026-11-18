package com.ektrepha.banner.controller;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.ektrepha.banner.dto.request.CreateBannerRequest;
import com.ektrepha.banner.dto.request.ReorderBannersRequest;
import com.ektrepha.banner.dto.request.SetBannerActiveRequest;
import com.ektrepha.banner.dto.request.UpdateBannerRequest;
import com.ektrepha.banner.dto.response.AdminBannerResponse;
import com.ektrepha.banner.dto.response.BannerImageUploadResponse;
import com.ektrepha.banner.service.BannerService;
import com.ektrepha.model.BannerStatus;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

// Role enforcement for the whole /api/v1/admin/** prefix is centralized in SecurityConfig
// (hasRole('ADMIN')), same as every other AdminXController - see AdminNannyController.
// PRD's own role table (Super admin/Content manager write, others read-only) isn't enforced here:
// a "Content manager" role doesn't exist in UserType yet, same gap the Care Manager feature had
// for CARE_MANAGER before it was added - noted for a follow-up rather than silently dropped.
@RestController
@RequestMapping("/api/v1/admin/banners")
@RequiredArgsConstructor
public class AdminBannerController {

	private final BannerService bannerService;

	@GetMapping
	public ResponseEntity<List<AdminBannerResponse>> list(@RequestParam(required = false) BannerStatus status) {
		return ResponseEntity.ok(bannerService.list(status));
	}

	@GetMapping("/{id}")
	public ResponseEntity<AdminBannerResponse> get(@PathVariable UUID id) {
		return ResponseEntity.ok(bannerService.get(id));
	}

	@PostMapping
	public ResponseEntity<AdminBannerResponse> create(@Valid @RequestBody CreateBannerRequest request, Authentication authentication) {
		return ResponseEntity.status(HttpStatus.CREATED).body(bannerService.create(request, actorId(authentication)));
	}

	@PutMapping("/{id}")
	public ResponseEntity<AdminBannerResponse> update(@PathVariable UUID id, @Valid @RequestBody UpdateBannerRequest request,
			Authentication authentication) {
		return ResponseEntity.ok(bannerService.update(id, request, actorId(authentication)));
	}

	@PatchMapping("/{id}/active")
	public ResponseEntity<AdminBannerResponse> setActive(@PathVariable UUID id, @Valid @RequestBody SetBannerActiveRequest request,
			Authentication authentication) {
		return ResponseEntity.ok(bannerService.setActive(id, request.active(), actorId(authentication)));
	}

	@PostMapping("/{id}/duplicate")
	public ResponseEntity<AdminBannerResponse> duplicate(@PathVariable UUID id, Authentication authentication) {
		return ResponseEntity.status(HttpStatus.CREATED).body(bannerService.duplicate(id, actorId(authentication)));
	}

	@PutMapping("/reorder")
	public ResponseEntity<Void> reorder(@Valid @RequestBody ReorderBannersRequest request) {
		bannerService.reorder(request);
		return ResponseEntity.noContent().build();
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable UUID id) {
		bannerService.softDelete(id);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/upload")
	public ResponseEntity<BannerImageUploadResponse> upload(@RequestParam MultipartFile file) {
		try {
			return ResponseEntity.ok(bannerService.uploadImage(file.getBytes(), file.getOriginalFilename(), file.getContentType()));
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private Long actorId(Authentication authentication) {
		return Long.valueOf(authentication.getName());
	}

}
