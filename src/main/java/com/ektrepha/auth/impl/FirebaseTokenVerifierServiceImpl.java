package com.ektrepha.auth.impl;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.ektrepha.exception.InvalidFirebaseTokenException;
import com.ektrepha.config.properties.AppProperties;
import com.ektrepha.auth.security.FirebaseTokenVerifierService;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import com.google.firebase.auth.FirebaseAuthException;

import lombok.extern.slf4j.Slf4j;

/**
 * Unlike {@link GoogleIdTokenVerifierServiceImpl}, initialization here needs to open a
 * real credentials file, which won't exist in dev until app.firebase.service-account-path
 * is configured — so failure is logged and swallowed rather than crashing the app on boot;
 * {@link #verify} fails loudly instead, only when phone auth is actually attempted.
 */
@Slf4j
@Component
public class FirebaseTokenVerifierServiceImpl implements FirebaseTokenVerifierService {

	private FirebaseApp firebaseApp;

	public FirebaseTokenVerifierServiceImpl(AppProperties appProperties) {
		// FirebaseApp.initializeApp registers a process-wide singleton by name that outlives a
		// single Spring context — a devtools hot-restart re-runs this constructor in the same
		// JVM and would otherwise crash the whole app on "FirebaseApp name [DEFAULT] already
		// exists!", so reuse the existing instance instead of re-initializing.
		FirebaseApp existing = FirebaseApp.getApps().stream()
				.filter(app -> app.getName().equals(FirebaseApp.DEFAULT_APP_NAME))
				.findFirst()
				.orElse(null);
		if (existing != null) {
			this.firebaseApp = existing;
			return;
		}

		String path = appProperties.firebase().serviceAccountPath();
		try (FileInputStream serviceAccount = new FileInputStream(path)) {
			FirebaseOptions options = FirebaseOptions.builder()
					.setCredentials(GoogleCredentials.fromStream(serviceAccount))
					.build();
			this.firebaseApp = FirebaseApp.initializeApp(options);
		} catch (IOException ex) {
			log.warn("Firebase Admin SDK not initialized (app.firebase.service-account-path={}): {}. "
					+ "Phone signup/reset will fail until this points to a real service account key.", path, ex.getMessage());
			this.firebaseApp = null;
		}
	}

	@Override
	public FirebaseIdentity verify(String idToken) {
		if (firebaseApp == null) {
			throw new IllegalStateException("Firebase Admin SDK is not initialized — check app.firebase.service-account-path");
		}

		FirebaseToken token;
		try {
			token = FirebaseAuth.getInstance(firebaseApp).verifyIdToken(idToken);
		} catch (FirebaseAuthException | IllegalArgumentException ex) {
			log.warn("Firebase ID token verification threw: {}", ex.getMessage());
			throw new InvalidFirebaseTokenException("Could not verify Firebase ID token", ex);
		}

		Object firebaseClaim = token.getClaims().get("firebase");
		String signInProvider = firebaseClaim instanceof Map<?, ?> map ? String.valueOf(map.get("sign_in_provider")) : null;
		if (!"phone".equals(signInProvider)) {
			log.warn("Firebase ID token rejected: sign_in_provider={}, expected \"phone\"", signInProvider);
			throw new InvalidFirebaseTokenException("Firebase ID token was not issued via phone authentication");
		}

		String phoneNumber = (String) token.getClaims().get("phone_number");
		if (phoneNumber == null || phoneNumber.isBlank()) {
			log.warn("Firebase ID token has no phone_number claim, uid={}", token.getUid());
			throw new InvalidFirebaseTokenException("Firebase ID token has no verified phone number");
		}

		log.debug("Verified Firebase ID token: uid={}, phoneNumber={}", token.getUid(), phoneNumber);
		return new FirebaseIdentity(token.getUid(), phoneNumber);
	}

}
