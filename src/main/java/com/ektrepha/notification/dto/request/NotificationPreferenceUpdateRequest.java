package com.ektrepha.notification.dto.request;

import java.util.List;

import com.ektrepha.model.NotificationCategory;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public record NotificationPreferenceUpdateRequest(@NotEmpty @Valid List<CategoryUpdate> preferences) {

	public record CategoryUpdate(
			@NotNull NotificationCategory category,
			@NotNull Boolean pushEnabled,
			@NotNull Boolean smsEnabled,
			@NotNull Boolean emailEnabled) {
	}
}
