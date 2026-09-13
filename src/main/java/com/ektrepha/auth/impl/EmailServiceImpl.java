package com.ektrepha.auth.impl;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.ektrepha.model.OtpPurpose;
import com.ektrepha.auth.service.EmailService;
import com.ektrepha.config.properties.AppProperties;

import lombok.extern.slf4j.Slf4j;

/** Sends transactional email via Brevo (https://api.brevo.com/v3/smtp/email). */
@Slf4j
@Service
public class EmailServiceImpl implements EmailService {

	private final RestClient brevoClient;
	private final String senderEmail;
	private final String senderName;

	public EmailServiceImpl(AppProperties appProperties) {
		AppProperties.Email.Brevo brevo = appProperties.email().brevo();
		this.senderEmail = brevo.senderEmail();
		this.senderName = brevo.senderName();
		this.brevoClient = RestClient.builder()
				.baseUrl("https://api.brevo.com/v3")
				.defaultHeader("api-key", brevo.apiKey())
				.defaultHeader("Content-Type", "application/json")
				.defaultHeader("Accept", "application/json")
				.build();
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

	private void send(String toEmail, String subject, String htmlContent) {
		try {
			brevoClient.post()
					.uri("/smtp/email")
					.body(new BrevoEmailRequest(new BrevoSender(senderName, senderEmail), List.of(new BrevoRecipient(toEmail)), subject, htmlContent))
					.retrieve()
					.toBodilessEntity();
		} catch (RestClientException e) {
			// Notification email is best-effort — a Brevo failure (e.g. no real API key in dev)
			// shouldn't fail the operation that triggered it (signup, waitlist join, etc.).
			log.warn("Failed to send email to {} via Brevo: {}", toEmail, e.getMessage());
		}
	}

	private record BrevoSender(String name, String email) {
	}

	private record BrevoRecipient(String email) {
	}

	private record BrevoEmailRequest(BrevoSender sender, List<BrevoRecipient> to, String subject, String htmlContent) {
	}

}
