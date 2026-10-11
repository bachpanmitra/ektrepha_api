package com.ektrepha.notification.service;

import java.util.List;

import com.ektrepha.nannyapp.dto.response.NotificationItemResponse;

/** The in-app notification inbox (nanny app's Updates screen). Role-agnostic - keyed by user. */
public interface UserNotificationService {

	List<NotificationItemResponse> list(Long userId);

	void markRead(Long userId, String notificationId);

	void markAllRead(Long userId);

	/**
	 * Adds an inbox entry and sends the same title/body as a push. {@code route} is a nanny-app route
	 * name (see ektrepha-nanny-ui navigation.ts's routeFromDeepLink) or null; {@code shiftId} only for
	 * {@code shiftDetails}. Joins the caller's transaction, so the entry is only kept if the action it reports commits.
	 */
	void notify(Long userId, String title, String body, String route, String shiftId);

}
