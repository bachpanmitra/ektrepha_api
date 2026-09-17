package com.ektrepha.notification.service;

import java.util.List;

import com.ektrepha.notification.dto.request.DeviceRegisterRequest;
import com.ektrepha.notification.dto.request.NotificationPreferenceUpdateRequest;
import com.ektrepha.notification.dto.response.DeviceResponse;
import com.ektrepha.notification.dto.response.NotificationPreferenceResponse;

public interface NotificationService {

	// Always returns exactly one row per NotificationCategory value — a category with no saved row
	// yet defaults to every channel enabled, matching the DB column defaults.
	List<NotificationPreferenceResponse> getPreferences(Long userId);

	List<NotificationPreferenceResponse> updatePreferences(Long userId, NotificationPreferenceUpdateRequest request);

	// Idempotent register — re-registering an existing token just refreshes its owner/last-active
	// timestamp rather than erroring (push_token is globally unique, e.g. a device re-logging-in
	// as a different user).
	DeviceResponse registerDevice(Long userId, DeviceRegisterRequest request);

}
