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
	 * and swallowed rather than thrown. Returns whether the send actually succeeded, so callers
	 * don't log a false "sent" message on top of the warning logged here.
	 */
	protected boolean send(String toEmail, String subject, String htmlContent) {
		try {
			brevoClient.post()
					.uri("/smtp/email")
					.body(new BrevoEmailRequest(new BrevoSender(senderName, senderEmail), List.of(new BrevoRecipient(toEmail)), subject, wrapInTemplate(htmlContent)))
					.retrieve()
					.toBodilessEntity();
			return true;
		} catch (RestClientException e) {
			log.warn("Failed to send email to {} via Brevo: {}", toEmail, e.getMessage());
			return false;
		}
	}

	/** Wraps a service's inner HTML fragment in the shared Ektrepha branded email shell. */
	private static String wrapInTemplate(String bodyHtml) {
		return """
				<!DOCTYPE html>
				<html>
				  <body style="margin:0;padding:0;background-color:#F1EAD9;font-family:Helvetica,Arial,sans-serif;">
				    <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="background-color:#F1EAD9;padding:32px 16px;">
				      <tr>
				        <td align="center">
				          <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="max-width:480px;background-color:#ffffff;border-radius:16px;overflow:hidden;">
				            <tr>
				              <td style="background-color:#15453D;padding:24px 32px;">
				                <span style="color:#ffffff;font-size:20px;font-weight:800;letter-spacing:0.3px;">Ektrepha</span>
				              </td>
				            </tr>
				            <tr>
				              <td style="padding:32px;color:#1B332D;font-size:15px;line-height:1.6;">
				                %s
				              </td>
				            </tr>
				            <tr>
				              <td style="padding:20px 32px;border-top:1px solid #DCD3C4;color:#8a8378;font-size:12px;line-height:1.5;">
				                You're receiving this because of activity on your Ektrepha account. If this wasn't you, you can safely ignore this email.
				              </td>
				            </tr>
				          </table>
				        </td>
				      </tr>
				    </table>
				  </body>
				</html>
				""".formatted(bodyHtml);
	}

	private record BrevoSender(String name, String email) {
	}

	private record BrevoRecipient(String email) {
	}

	private record BrevoEmailRequest(BrevoSender sender, List<BrevoRecipient> to, String subject, String htmlContent) {
	}

}
