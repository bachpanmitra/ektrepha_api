package com.ektrepha.notification.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ektrepha.notification.dto.request.DeviceRegisterRequest;
import com.ektrepha.notification.dto.request.NotificationPreferenceUpdateRequest;
import com.ektrepha.notification.dto.response.DeviceResponse;
import com.ektrepha.notification.dto.response.NotificationPreferenceResponse;
import com.ektrepha.notification.service.NotificationService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** A5 — preference storage only. No push/SMS/email delivery is wired to any of this yet (PRD v2 §17 hard blocker). */
@RestController
@RequestMapping("/api/v1/users/me")
@RequiredArgsConstructor
public class NotificationController {

	private final NotificationService notificationService;

	@GetMapping("/notification-preferences")
	public ResponseEntity<List<NotificationPreferenceResponse>> getPreferences(Authentication authentication) {
		return ResponseEntity.ok(notificationService.getPreferences(userId(authentication)));
	}

	@PutMapping("/notification-preferences")
	public ResponseEntity<List<NotificationPreferenceResponse>> updatePreferences(Authentication authentication,
			@Valid @RequestBody NotificationPreferenceUpdateRequest request) {
		return ResponseEntity.ok(notificationService.updatePreferences(userId(authentication), request));
	}

	@PostMapping("/devices")
	public ResponseEntity<DeviceResponse> registerDevice(Authentication authentication, @Valid @RequestBody DeviceRegisterRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(notificationService.registerDevice(userId(authentication), request));
	}

	private Long userId(Authentication authentication) {
		return Long.valueOf(authentication.getName());
	}

}
