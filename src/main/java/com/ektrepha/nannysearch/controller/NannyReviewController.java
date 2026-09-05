package com.ektrepha.nannysearch.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ektrepha.nannysearch.dto.request.ReviewCreateRequest;
import com.ektrepha.nannysearch.dto.response.ReviewResponse;
import com.ektrepha.nannysearch.service.NannyReviewService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/reviews")
@RequiredArgsConstructor
public class NannyReviewController {

	private final NannyReviewService nannyReviewService;

	// Submits a parent's post-booking rating/comment for a completed booking.
	@PostMapping
	@PreAuthorize("hasRole('PARENT')")
	public ResponseEntity<ReviewResponse> submitReview(Authentication authentication, @Valid @RequestBody ReviewCreateRequest request) {
		Long userId = Long.valueOf(authentication.getName());
		return ResponseEntity.status(HttpStatus.CREATED).body(nannyReviewService.submitReview(userId, request));
	}

}
