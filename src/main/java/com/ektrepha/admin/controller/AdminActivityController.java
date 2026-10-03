package com.ektrepha.admin.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ektrepha.activity.service.OrderActivityService;
import com.ektrepha.admin.dto.response.AdminOrderActivityFeedResponse;
import com.ektrepha.model.OrderActivityType;

import lombok.RequiredArgsConstructor;

// Role enforcement for the whole /api/v1/admin/** prefix is centralized in SecurityConfig
// (hasRole('ADMIN')) rather than repeated per-controller with @PreAuthorize.
/** Cross-booking "every stage, every booking, when it changed" activity feed for ops. Scoped to a
 * single booking's own activity lives on {@link AdminBookingController} instead. */
@RestController
@RequestMapping("/api/v1/admin/activity")
@RequiredArgsConstructor
public class AdminActivityController {

	private final OrderActivityService orderActivityService;

	@GetMapping
	public ResponseEntity<AdminOrderActivityFeedResponse> feed(
			@RequestParam(required = false) OrderActivityType eventType,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return ResponseEntity.ok(orderActivityService.findFeed(eventType, page, size));
	}

}
