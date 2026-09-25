package com.ektrepha.sms;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.ektrepha.config.properties.AppProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.extern.slf4j.Slf4j;

/**
 * Base class for sending transactional SMS via MSG91's Flow API
 * (https://control.msg91.com/api/v5/flow). Any service that needs to send SMS (OTP today; booking
 * alerts, etc. later) extends this and calls {@link #send(String, String)} — the MSG91 wiring
 * lives here once, not per-service. Mirrors {@code AbstractEmailSender}'s shape.
 * <p>
 * MSG91 templates are DLT-registered ahead of time in the MSG91 dashboard (a legal requirement for
 * transactional SMS to Indian numbers) — {@code templateId} and {@code otpVariableName} must match
 * whatever template was actually registered there.
 */
@Slf4j
public abstract class AbstractSmsSender {

	private final RestClient msg91Client;
	private final String templateId;
	private final String otpVariableName;
	private final String senderId;

	protected AbstractSmsSender(AppProperties appProperties) {
		AppProperties.Sms.Msg91 msg91 = appProperties.sms().msg91();
		this.templateId = msg91.templateId();
		this.otpVariableName = msg91.otpVariableName();
		this.senderId = msg91.senderId();
		this.msg91Client = RestClient.builder()
				.baseUrl("https://control.msg91.com/api/v5")
				.defaultHeader("authkey", msg91.authKey())
				.defaultHeader("Content-Type", "application/json")
				.defaultHeader("Accept", "application/json")
				.build();
	}

	/**
	 * Best-effort send — an MSG91 failure (e.g. no real API key in dev) shouldn't fail the
	 * operation that triggered it, so failures are logged and swallowed rather than thrown.
	 * Returns whether the send actually succeeded, so callers don't log a false "sent" message on
	 * top of the warning logged here. The OTP itself is never logged — only success/failure.
	 */
	protected boolean send(String e164MobileNumber, String otp) {
		Map<String, Object> recipient = new HashMap<>();
		recipient.put("mobiles", toMsg91MobileFormat(e164MobileNumber));
		recipient.put(otpVariableName, otp);
		if (senderId != null && !senderId.isBlank()) {
			recipient.put("SENDERID", senderId);
		}

		try {
			msg91Client.post()
					.uri("/flow")
					.body(new Msg91FlowRequest(templateId, List.of(recipient)))
					.retrieve()
					.toBodilessEntity();
			return true;
		} catch (RestClientException e) {
			log.warn("Failed to send SMS to {} via MSG91: {}", e164MobileNumber, e.getMessage());
			return false;
		}
	}

	/** MSG91 expects a bare national number (e.g. "919876543210"), not the leading '+' of E.164. */
	private static String toMsg91MobileFormat(String e164MobileNumber) {
		return e164MobileNumber.startsWith("+") ? e164MobileNumber.substring(1) : e164MobileNumber;
	}

	private record Msg91FlowRequest(@JsonProperty("template_id") String templateId, List<Map<String, Object>> recipients) {
	}

}
