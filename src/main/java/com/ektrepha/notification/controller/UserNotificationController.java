package com.ektrepha.notification.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ektrepha.nannyapp.dto.response.NotificationItemResponse;
import com.ektrepha.notification.service.UserNotificationService;

import lombok.RequiredArgsConstructor;

/** The caller's own in-app notification inbox. Any authenticated user. */
@RestController
@RequestMapping("/api/v1/users/me/notifications")
@RequiredArgsConstructor
public class UserNotificationController {

	private final UserNotificationService userNotificationService;

	@GetMapping
	public ResponseEntity<List<NotificationItemResponse>> list(Authentication authentication) {
		return ResponseEntity.ok(userNotificationService.list(userId(authentication)));
	}

	@PostMapping("/{id}/read")
	public ResponseEntity<Void> markRead(Authentication authentication, @PathVariable String id) {
		userNotificationService.markRead(userId(authentication), id);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/read-all")
	public ResponseEntity<Void> markAllRead(Authentication authentication) {
		userNotificationService.markAllRead(userId(authentication));
		return ResponseEntity.noContent().build();
	}

	private Long userId(Authentication authentication) {
		return Long.valueOf(authentication.getName());
	}

}
