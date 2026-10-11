package com.ektrepha.nannyapp.dto.response;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Matches the nanny app's {@code NotificationItem}. deepLink is null when the notification opens nothing. */
public record NotificationItemResponse(
		String id, String title, String body, Instant createdAt, boolean read, DeepLink deepLink) {

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record DeepLink(String route, String shiftId) {
	}

}
