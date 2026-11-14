package com.ektrepha.caremanager.impl;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.ektrepha.auth.service.EmailService;
import com.ektrepha.caremanager.event.CareManagerCreatedEvent;
import com.ektrepha.model.CareManager;
import com.ektrepha.model.PasswordResetToken;
import com.ektrepha.repository.CareManagerRepository;
import com.ektrepha.repository.PasswordResetTokenRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Sends the new Care Manager's "set your password" email strictly after the create commits —
 * same shape as WaitlistSignupServiceImpl's password-reset-token flow (a real, consumable token,
 * not the broken throwaway UUID in EmailServiceImpl#sendPasswordSetupEmail).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CareManagerWelcomeEmailListener {

	private static final Duration TOKEN_TTL = Duration.ofHours(24);
	private static final SecureRandom SECURE_RANDOM = new SecureRandom();

	private final CareManagerRepository careManagerRepository;
	private final PasswordResetTokenRepository passwordResetTokenRepository;
	private final EmailService emailService;

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void onCareManagerCreated(CareManagerCreatedEvent event) {
		CareManager careManager = careManagerRepository.findById(event.careManagerId()).orElse(null);
		if (careManager == null) {
			log.warn("Skipping welcome email: care manager {} no longer exists", event.careManagerId());
			return;
		}

		String token = generateOpaqueToken();
		passwordResetTokenRepository.save(PasswordResetToken.builder()
				.user(careManager.getUser())
				.token(token)
				.expiresAt(Instant.now().plus(TOKEN_TTL))
				.build());

		String link = "https://app.ektrepha.com/set-password?token=" + token;
		emailService.sendPasswordResetEmail(careManager.getEmail(), link);
	}

	private String generateOpaqueToken() {
		byte[] bytes = new byte[32];
		SECURE_RANDOM.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

}
