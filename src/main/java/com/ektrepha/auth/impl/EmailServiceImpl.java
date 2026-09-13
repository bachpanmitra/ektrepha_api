package com.ektrepha.auth.impl;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.ektrepha.model.OtpPurpose;
import com.ektrepha.auth.service.EmailService;
import com.ektrepha.config.properties.AppProperties;
import com.ektrepha.email.AbstractEmailSender;

import lombok.extern.slf4j.Slf4j;

/** Sends auth-related transactional email via Brevo (see {@link AbstractEmailSender}). */
@Slf4j
@Service
public class EmailServiceImpl extends AbstractEmailSender implements EmailService {

	public EmailServiceImpl(AppProperties appProperties) {
		super(appProperties);
	}

	@Override
	public void sendOtpEmail(String email, String otp, OtpPurpose purpose) {
		send(email, "Your Ektrepha verification code",
				"<p>Your one-time code is <strong>%s</strong>. It expires shortly — don't share it with anyone.</p>"
						.formatted(otp));
		log.info("Sent OTP email to {} (purpose={})", email, purpose);
	}

	@Override
	public void sendPasswordSetupEmail(String email) {
		String link = "https://app.ektrepha.com/set-password?token=" + UUID.randomUUID();
		send(email, "Set up your Ektrepha password",
				"<p>Set a password so you can also log in with email/password: <a href=\"%s\">%s</a></p>"
						.formatted(link, link));
		log.info("Sent password setup email to {}", email);
	}

	@Override
	public void sendPasswordResetEmail(String email, String resetLink) {
		send(email, "Set your Ektrepha password",
				"<p>You're on the Ektrepha waitlist. Set a password to finish creating your account: <a href=\"%s\">%s</a></p>"
						.formatted(resetLink, resetLink));
		log.info("Sent password reset email to {}", email);
	}

	@Override
	public void sendVerificationEmail(String email) {
		String link = "https://app.ektrepha.com/verify-email?token=" + UUID.randomUUID();
		send(email, "Verify your Ektrepha email",
				"<p>Verify your email address: <a href=\"%s\">%s</a></p>".formatted(link, link));
		log.info("Sent verification email to {}", email);
	}

}
