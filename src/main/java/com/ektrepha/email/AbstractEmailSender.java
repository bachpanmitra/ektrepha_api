package com.ektrepha.email;

import java.util.List;

import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.ektrepha.config.properties.AppProperties;

import lombok.extern.slf4j.Slf4j;

/**
 * Base class for sending transactional email via Brevo (https://api.brevo.com/v3/smtp/email).
 * Any service that needs to send email (OTP, booking confirmation, ...) extends this and calls
 * {@link #send(String, String, String)} — the Brevo wiring lives here once, not per-service.
 */
@Slf4j
public abstract class AbstractEmailSender {

	private final RestClient brevoClient;
	private final String senderEmail;
	private final String senderName;

	protected AbstractEmailSender(AppProperties appProperties) {
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

	/**
	 * Best-effort send — a Brevo failure (e.g. no real API key in dev) shouldn't fail the
	 * operation that triggered it (signup, waitlist join, booking, etc.), so failures are logged
	 * and swallowed rather than thrown.
	 */
	protected void send(String toEmail, String subject, String htmlContent) {
		try {
			brevoClient.post()
					.uri("/smtp/email")
					.body(new BrevoEmailRequest(new BrevoSender(senderName, senderEmail), List.of(new BrevoRecipient(toEmail)), subject, htmlContent))
					.retrieve()
					.toBodilessEntity();
		} catch (RestClientException e) {
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
