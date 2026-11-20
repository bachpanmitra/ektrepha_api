package com.ektrepha.notification.dto.response;

import java.time.Instant;

/** One row of a user's in-app notification inbox. {@code route}/{@code bookingId} are the deep-link the client's NotificationDeepLink needs - flat here, nested client-side. */
public record NotificationItemResponse(Long id, String title, String body, Instant createdAt, boolean read, String route, Long bookingId) {
}
