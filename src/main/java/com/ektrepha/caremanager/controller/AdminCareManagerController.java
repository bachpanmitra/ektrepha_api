package com.ektrepha.caremanager.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ektrepha.caremanager.dto.request.ConnectRequest;
import com.ektrepha.caremanager.dto.request.CreateCareManagerRequest;
import com.ektrepha.caremanager.dto.request.UpdateCareManagerRequest;
import com.ektrepha.caremanager.dto.response.CareManagerAdminResponse;
import com.ektrepha.caremanager.dto.response.CareManagerListResponse;
import com.ektrepha.caremanager.dto.response.ConnectResponse;
import com.ektrepha.caremanager.dto.response.PhotoUrlResponse;
import com.ektrepha.caremanager.service.AdminCareManagerService;
import com.ektrepha.exception.UserNotFoundException;
import com.ektrepha.model.User;
import com.ektrepha.repository.UserRepository;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** docs/api/openapi.yaml tag "Admin" — role ADMIN only (SecurityConfig: /v1/admin/**). */
@RestController
@RequestMapping("/v1/admin/care-managers")
@RequiredArgsConstructor
public class AdminCareManagerController {

	private final AdminCareManagerService adminCareManagerService;
	private final UserRepository userRepository;

	@GetMapping
	public CareManagerListResponse list() {
		return adminCareManagerService.list();
	}

	@PostMapping
	public ResponseEntity<CareManagerAdminResponse> create(@Valid @RequestBody CreateCareManagerRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(adminCareManagerService.create(request));
	}

	@PatchMapping("/{careManagerId}")
	public CareManagerAdminResponse update(@PathVariable String careManagerId, @RequestBody UpdateCareManagerRequest request) {
		return adminCareManagerService.update(careManagerId, request);
	}

	@PutMapping(path = "/{careManagerId}/photo", consumes = { MediaType.IMAGE_JPEG_VALUE, MediaType.IMAGE_PNG_VALUE })
	public PhotoUrlResponse uploadPhoto(@PathVariable String careManagerId, @RequestBody byte[] content,
			@RequestHeader("Content-Type") String contentType) {
		return adminCareManagerService.uploadPhoto(careManagerId, content, contentType);
	}

	@PostMapping("/{careManagerId}/connect")
	public ConnectResponse connect(@PathVariable String careManagerId, @Valid @RequestBody ConnectRequest request,
			Authentication authentication) {
		return adminCareManagerService.connect(careManagerId, request, admin(authentication));
	}

	@PostMapping("/{careManagerId}/deactivate")
	public CareManagerAdminResponse deactivate(@PathVariable String careManagerId) {
		return adminCareManagerService.deactivate(careManagerId);
	}

	private User admin(Authentication authentication) {
		Long userId = Long.valueOf(authentication.getName());
		return userRepository.findById(userId).orElseThrow(() -> new UserNotFoundException("No user with id " + userId));
	}

}
