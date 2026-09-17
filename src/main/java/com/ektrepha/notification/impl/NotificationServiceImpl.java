package com.ektrepha.notification.impl;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ektrepha.exception.UserNotFoundException;
import com.ektrepha.model.NotificationCategory;
import com.ektrepha.model.User;
import com.ektrepha.model.UserDevice;
import com.ektrepha.model.UserNotificationPreference;
import com.ektrepha.notification.dto.request.DeviceRegisterRequest;
import com.ektrepha.notification.dto.request.NotificationPreferenceUpdateRequest;
import com.ektrepha.notification.dto.response.DeviceResponse;
import com.ektrepha.notification.dto.response.NotificationPreferenceResponse;
import com.ektrepha.notification.service.NotificationService;
import com.ektrepha.repository.UserDeviceRepository;
import com.ektrepha.repository.UserNotificationPreferenceRepository;
import com.ektrepha.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

	// Transactional-safety categories are never opt-out-able (PRD v2 §9.4) — enforced here, not
	// just documented, so a client bug or a crafted request can't silently turn one off.
	private static final Set<NotificationCategory> NON_EDITABLE = EnumSet.of(
			NotificationCategory.BOOKING_CONFIRMATION, NotificationCategory.NANNY_EN_ROUTE, NotificationCategory.CARE_START_END);

	private final UserRepository userRepository;
	private final UserNotificationPreferenceRepository preferenceRepository;
	private final UserDeviceRepository deviceRepository;

	@Override
	@Transactional(readOnly = true)
	public List<NotificationPreferenceResponse> getPreferences(Long userId) {
		Map<NotificationCategory, UserNotificationPreference> byCategory = preferenceRepository.findByUserId(userId).stream()
				.collect(java.util.stream.Collectors.toMap(UserNotificationPreference::getCategory, p -> p));

		return List.of(NotificationCategory.values()).stream()
				.map(category -> toResponse(category, byCategory.get(category)))
				.toList();
	}

	@Override
	@Transactional
	public List<NotificationPreferenceResponse> updatePreferences(Long userId, NotificationPreferenceUpdateRequest request) {
		User user = userRepository.findById(userId).orElseThrow(() -> new UserNotFoundException("No user with id " + userId));

		for (NotificationPreferenceUpdateRequest.CategoryUpdate update : request.preferences()) {
			UserNotificationPreference preference = preferenceRepository.findByUserIdAndCategory(userId, update.category())
					.orElseGet(() -> UserNotificationPreference.builder().user(user).category(update.category()).build());

			if (NON_EDITABLE.contains(update.category())) {
				// Silently forced true rather than rejecting the request — the client shouldn't be
				// showing a toggle for these at all, so a mismatched payload is a client bug, not
				// something worth failing the whole batch over.
				preference.setPushEnabled(true);
				preference.setSmsEnabled(true);
				preference.setEmailEnabled(true);
			} else {
				preference.setPushEnabled(update.pushEnabled());
				preference.setSmsEnabled(update.smsEnabled());
				preference.setEmailEnabled(update.emailEnabled());
			}
			preferenceRepository.save(preference);
		}

		return getPreferences(userId);
	}

	@Override
	@Transactional
	public DeviceResponse registerDevice(Long userId, DeviceRegisterRequest request) {
		User user = userRepository.findById(userId).orElseThrow(() -> new UserNotFoundException("No user with id " + userId));

		UserDevice device = deviceRepository.findByPushToken(request.pushToken())
				.orElseGet(() -> UserDevice.builder().pushToken(request.pushToken()).build());
		device.setUser(user);
		device.setPlatform(request.platform());
		device.setLastActiveAt(Instant.now());
		device = deviceRepository.save(device);

		return new DeviceResponse(device.getId(), device.getPlatform(), device.getLastActiveAt());
	}

	private NotificationPreferenceResponse toResponse(NotificationCategory category, UserNotificationPreference preference) {
		boolean editable = !NON_EDITABLE.contains(category);
		if (preference == null) {
			// No saved row yet — defaults match the DB column defaults (every channel enabled).
			return new NotificationPreferenceResponse(category.name(), true, true, true, editable);
		}
		return new NotificationPreferenceResponse(category.name(), preference.isPushEnabled(), preference.isSmsEnabled(), preference.isEmailEnabled(), editable);
	}

}
