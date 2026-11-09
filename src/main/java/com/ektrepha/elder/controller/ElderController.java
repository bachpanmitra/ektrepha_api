package com.ektrepha.elder.controller;

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
import org.springframework.web.bind.annotation.RestController;

import com.ektrepha.elder.dto.request.ElderUpsertRequest;
import com.ektrepha.elder.dto.response.ElderDetailResponse;
import com.ektrepha.elder.dto.response.ElderSummaryResponse;
import com.ektrepha.elder.service.ElderService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Elder Care's profile CRUD — mirrors {@code com.ektrepha.child.controller.ChildController}. */
@RestController
@RequestMapping("/api/v1/parents/me/elders")
@RequiredArgsConstructor
public class ElderController {

	private final ElderService elderService;

	@GetMapping
	@PreAuthorize("hasRole('PARENT')")
	public ResponseEntity<List<ElderSummaryResponse>> list(Authentication authentication) {
		return ResponseEntity.ok(elderService.list(userId(authentication)));
	}

	@GetMapping("/{id}")
	@PreAuthorize("hasRole('PARENT')")
	public ResponseEntity<ElderDetailResponse> get(Authentication authentication, @PathVariable Long id) {
		return ResponseEntity.ok(elderService.get(userId(authentication), id));
	}

	@PostMapping
	@PreAuthorize("hasRole('PARENT')")
	public ResponseEntity<ElderDetailResponse> create(Authentication authentication, @Valid @RequestBody ElderUpsertRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(elderService.create(userId(authentication), request));
	}

	@PutMapping("/{id}")
	@PreAuthorize("hasRole('PARENT')")
	public ResponseEntity<ElderDetailResponse> update(Authentication authentication, @PathVariable Long id,
			@Valid @RequestBody ElderUpsertRequest request) {
		return ResponseEntity.ok(elderService.update(userId(authentication), id, request));
	}

	@DeleteMapping("/{id}")
	@PreAuthorize("hasRole('PARENT')")
	public ResponseEntity<Void> remove(Authentication authentication, @PathVariable Long id) {
		elderService.remove(userId(authentication), id);
		return ResponseEntity.noContent().build();
	}

	private Long userId(Authentication authentication) {
		return Long.valueOf(authentication.getName());
	}

}
