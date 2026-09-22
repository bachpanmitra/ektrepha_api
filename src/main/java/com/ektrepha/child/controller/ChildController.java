package com.ektrepha.child.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.ektrepha.child.dto.request.CareNotesUpdateRequest;
import com.ektrepha.child.dto.request.ChildUpsertRequest;
import com.ektrepha.child.dto.response.ChildDetailResponse;
import com.ektrepha.child.dto.response.ChildSummaryResponse;
import com.ektrepha.child.dto.response.GuardianResponse;
import com.ektrepha.child.service.ChildService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/parents/me/children")
@RequiredArgsConstructor
public class ChildController {

	private final ChildService childService;

	@GetMapping
	@PreAuthorize("hasRole('PARENT')")
	public ResponseEntity<List<ChildSummaryResponse>> list(Authentication authentication) {
		return ResponseEntity.ok(childService.list(userId(authentication)));
	}

	@GetMapping("/{id}")
	@PreAuthorize("hasRole('PARENT')")
	public ResponseEntity<ChildDetailResponse> get(Authentication authentication, @PathVariable Long id) {
		return ResponseEntity.ok(childService.get(userId(authentication), id));
	}

	@PostMapping
	@PreAuthorize("hasRole('PARENT')")
	public ResponseEntity<ChildDetailResponse> create(Authentication authentication, @Valid @RequestBody ChildUpsertRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(childService.create(userId(authentication), request));
	}

	@PutMapping("/{id}")
	@PreAuthorize("hasRole('PARENT')")
	public ResponseEntity<ChildDetailResponse> update(Authentication authentication, @PathVariable Long id,
			@Valid @RequestBody ChildUpsertRequest request) {
		return ResponseEntity.ok(childService.update(userId(authentication), id, request));
	}

	@PutMapping("/{id}/care-notes")
	@PreAuthorize("hasRole('PARENT')")
	public ResponseEntity<ChildDetailResponse> updateCareNotes(Authentication authentication, @PathVariable Long id,
			@RequestBody CareNotesUpdateRequest request) {
		return ResponseEntity.ok(childService.updateCareNotes(userId(authentication), id, request));
	}

	@PostMapping("/{id}/photo")
	@PreAuthorize("hasRole('PARENT')")
	public ResponseEntity<ChildDetailResponse> uploadPhoto(Authentication authentication, @PathVariable Long id,
			@RequestParam("file") MultipartFile file) {
		return ResponseEntity.ok(childService.uploadPhoto(userId(authentication), id, file));
	}

	@GetMapping("/{id}/guardians")
	@PreAuthorize("hasRole('PARENT')")
	public ResponseEntity<List<GuardianResponse>> guardians(Authentication authentication, @PathVariable Long id) {
		return ResponseEntity.ok(childService.guardians(userId(authentication), id));
	}

	@DeleteMapping("/{id}")
	@PreAuthorize("hasRole('PARENT')")
	public ResponseEntity<Void> remove(Authentication authentication, @PathVariable Long id) {
		childService.remove(userId(authentication), id);
		return ResponseEntity.noContent().build();
	}

	private Long userId(Authentication authentication) {
		return Long.valueOf(authentication.getName());
	}

}
