package com.ektrepha.notification.impl;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;

import org.springframework.stereotype.Component;

import com.ektrepha.config.properties.AppProperties;
import com.ektrepha.model.UserDevice;
import com.ektrepha.notification.service.PushSenderService;
import com.ektrepha.repository.UserDeviceRepository;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.Notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Same defensive FirebaseApp bootstrap as {@code FirebaseTokenVerifierServiceImpl} (reuse the
 * process-wide singleton if one of them already created it; log and disable rather than crash boot
 * if the service-account file isn't configured, e.g. local dev) — this is a separate small
 * component rather than sharing a bean because neither class today exposes one.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PushSenderServiceImpl implements PushSenderService {

	private final UserDeviceRepository userDeviceRepository;
	private final AppProperties appProperties;

	private FirebaseApp firebaseApp;
	private boolean initialized = false;

	@Override
	public void sendToUser(Long userId, String title, String body) {
		FirebaseApp app = firebaseApp();
		if (app == null) {
			log.info("Push skipped (Firebase not configured): userId={}, title={}", userId, title);
			return;
		}

		List<UserDevice> devices = userDeviceRepository.findByUserId(userId);
		if (devices.isEmpty()) {
			log.info("Push skipped (no registered devices): userId={}", userId);
			return;
		}

		for (UserDevice device : devices) {
			Message message = Message.builder()
					.setToken(device.getPushToken())
					.setNotification(Notification.builder().setTitle(title).setBody(body).build())
					.build();
			try {
				FirebaseMessaging.getInstance(app).send(message);
			} catch (FirebaseMessagingException ex) {
				if (ex.getMessagingErrorCode() == MessagingErrorCode.UNREGISTERED
						|| ex.getMessagingErrorCode() == MessagingErrorCode.INVALID_ARGUMENT) {
					log.info("Removing stale push token for userId={}: {}", userId, ex.getMessagingErrorCode());
					userDeviceRepository.deleteByPushToken(device.getPushToken());
				} else {
					log.warn("Push send failed for userId={}: {}", userId, ex.getMessage());
				}
			}
		}
	}

	// Lazy, not constructor-time: a devtools hot-restart re-runs the constructor in the same JVM,
	// but FirebaseApp.getApps() may not reflect another bean's just-created app yet at that point —
	// deferring to first actual use avoids a spurious "not configured" log on every restart.
	private synchronized FirebaseApp firebaseApp() {
		if (initialized) {
			return firebaseApp;
		}
		initialized = true;

		FirebaseApp existing = FirebaseApp.getApps().stream()
				.filter(a -> a.getName().equals(FirebaseApp.DEFAULT_APP_NAME))
				.findFirst()
				.orElse(null);
		if (existing != null) {
			firebaseApp = existing;
			return firebaseApp;
		}

		String path = appProperties.firebase().serviceAccountPath();
		try (FileInputStream serviceAccount = new FileInputStream(path)) {
			FirebaseOptions options = FirebaseOptions.builder()
					.setCredentials(GoogleCredentials.fromStream(serviceAccount))
					.build();
			firebaseApp = FirebaseApp.initializeApp(options);
		} catch (IOException ex) {
			log.warn("Firebase Admin SDK not initialized (app.firebase.service-account-path={}): {}. Push notifications will be skipped.", path, ex.getMessage());
			firebaseApp = null;
		}
		return firebaseApp;
	}

}
