package com.ektrepha.notification.impl;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ektrepha.exception.NannyAppResourceNotFoundException;
import com.ektrepha.model.UserNotification;
import com.ektrepha.nannyapp.dto.response.NotificationItemResponse;
import com.ektrepha.notification.service.PushSenderService;
import com.ektrepha.notification.service.UserNotificationService;
import com.ektrepha.repository.UserNotificationRepository;
import com.ektrepha.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserNotificationServiceImpl implements UserNotificationService {

	private final UserNotificationRepository userNotificationRepository;
	private final UserRepository userRepository;
	private final PushSenderService pushSenderService;

	@Override
	@Transactional(readOnly = true)
	public List<NotificationItemResponse> list(Long userId) {
		return userNotificationRepository.findTop100ByUserIdOrderByCreatedAtDesc(userId).stream()
				.map(n -> new NotificationItemResponse(String.valueOf(n.getId()), n.getTitle(), n.getBody(), n.getCreatedAt(),
						n.getReadAt() != null,
						n.getDeepLinkRoute() == null ? null : new NotificationItemResponse.DeepLink(n.getDeepLinkRoute(), n.getDeepLinkShiftId())))
				.toList();
	}

	@Override
	@Transactional
	public void markRead(Long userId, String notificationId) {
		long id;
		try {
			id = Long.parseLong(notificationId);
		} catch (NumberFormatException e) {
			throw new NannyAppResourceNotFoundException("No notification with id " + notificationId);
		}
		UserNotification notification = userNotificationRepository.findByIdAndUserId(id, userId)
				.orElseThrow(() -> new NannyAppResourceNotFoundException("No notification with id " + notificationId));
		if (notification.getReadAt() == null) {
			notification.setReadAt(Instant.now());
			userNotificationRepository.save(notification);
		}
	}

	@Override
	@Transactional
	public void markAllRead(Long userId) {
		userNotificationRepository.markAllRead(userId, Instant.now());
	}

	@Override
	@Transactional
	public void notify(Long userId, String title, String body, String route, String shiftId) {
		userNotificationRepository.save(UserNotification.builder()
				.user(userRepository.getReferenceById(userId)).title(title).body(body == null ? "" : body)
				.deepLinkRoute(route).deepLinkShiftId(shiftId).build());
		pushSenderService.sendToUser(userId, title, body);
	}

}
