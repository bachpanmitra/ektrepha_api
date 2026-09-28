package com.ektrepha.payment.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.ektrepha.config.properties.AppProperties;
import com.ektrepha.payment.dto.GatewayOrder;
import com.ektrepha.payment.dto.GatewayPaymentStatus;
import com.ektrepha.payment.service.PaymentGatewayService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

/**
 * Talks to Razorpay's plain REST API directly (Basic Auth, key id/secret) rather than pulling in
 * their SDK - same "build the RestClient inline, no shared bean" convention as
 * {@code OlaMapsClientImpl}/{@code NominatimGeocodingProvider}/{@code AbstractSmsSender}.
 * <p>
 * If {@code app.razorpay.key-id}/{@code key-secret} are blank (the dev/stage default - see
 * {@link AppProperties.Razorpay}), every method short-circuits to a local stand-in instead of
 * calling out or verifying anything, so the app stays usable (and tests never hit the real network)
 * with no Razorpay account configured — this reproduces exactly the trust-the-caller behavior
 * {@code HourlyCareServiceImpl} had before this gateway existed.
 */
@Slf4j
@Component
public class RazorpayGatewayServiceImpl implements PaymentGatewayService {

	private static final ObjectMapper MAPPER = new ObjectMapper();
	private static final HexFormat HEX = HexFormat.of();

	private final RestClient restClient;
	private final String keyId;
	private final String keySecret;
	private final String webhookSecret;

	public RazorpayGatewayServiceImpl(AppProperties appProperties) {
		AppProperties.Razorpay config = appProperties.razorpay();
		this.keyId = config.keyId();
		this.keySecret = config.keySecret();
		this.webhookSecret = config.webhookSecret();
		this.restClient = RestClient.builder()
				.baseUrl(config.baseUrl())
				.defaultHeader("Accept", "application/json")
				.build();
		if (!configured()) {
			log.warn("app.razorpay.key-id/key-secret are not set — order creation and payment/webhook "
					+ "signature verification will all use a local stand-in instead of calling Razorpay");
		}
	}

	@Override
	public GatewayOrder createOrder(BigDecimal amount, String receipt) {
		if (!configured()) {
			return new GatewayOrder("LOCAL_" + UUID.randomUUID(), "", amount, "INR");
		}

		long amountPaise = amount.setScale(2, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).longValueExact();
		String credentials = Base64.getEncoder().encodeToString((keyId + ":" + keySecret).getBytes(StandardCharsets.UTF_8));

		String body = restClient.post()
				.uri("/orders")
				.header("Authorization", "Basic " + credentials)
				.contentType(org.springframework.http.MediaType.APPLICATION_JSON)
				.body(java.util.Map.of("amount", amountPaise, "currency", "INR", "receipt", receipt, "payment_capture", 1))
				.retrieve()
				.body(String.class);

		JsonNode order = parse(body);
		String orderId = order.path("id").asText(null);
		if (orderId == null) {
			throw new IllegalStateException("Razorpay order creation did not return an order id: " + body);
		}
		return new GatewayOrder(orderId, keyId, amount, "INR");
	}

	@Override
	public boolean verifyPaymentSignature(String orderId, String paymentId, String signature) {
		if (!configured()) {
			return true;
		}
		if (orderId == null || paymentId == null || signature == null) {
			return false;
		}
		String expected = hmacHex(orderId + "|" + paymentId, keySecret);
		return constantTimeEquals(expected, signature);
	}

	@Override
	public boolean verifyWebhookSignature(String payload, String signatureHeader) {
		if (!configured()) {
			return true;
		}
		if (payload == null || signatureHeader == null || webhookSecret == null || webhookSecret.isBlank()) {
			return false;
		}
		String expected = hmacHex(payload, webhookSecret);
		return constantTimeEquals(expected, signatureHeader);
	}

	@Override
	public Optional<GatewayPaymentStatus> findLatestPayment(String orderId) {
		if (!configured()) {
			return Optional.empty();
		}

		String credentials = Base64.getEncoder().encodeToString((keyId + ":" + keySecret).getBytes(StandardCharsets.UTF_8));
		String body = restClient.get()
				.uri("/orders/{orderId}/payments", orderId)
				.header("Authorization", "Basic " + credentials)
				.retrieve()
				.body(String.class);

		boolean anyFailed = false;
		for (JsonNode item : parse(body).path("items")) {
			String status = item.path("status").asText("");
			if ("captured".equals(status)) {
				return Optional.of(new GatewayPaymentStatus(item.path("id").asText(null), true));
			}
			if ("failed".equals(status)) {
				anyFailed = true;
			}
		}
		return anyFailed ? Optional.of(new GatewayPaymentStatus(null, false)) : Optional.empty();
	}

	private boolean configured() {
		return keyId != null && !keyId.isBlank() && keySecret != null && !keySecret.isBlank();
	}

	private static String hmacHex(String data, String secret) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
			return HEX.formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException | InvalidKeyException e) {
			throw new IllegalStateException("Unable to compute Razorpay HMAC signature", e);
		}
	}

	private static boolean constantTimeEquals(String expected, String actual) {
		return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
	}

	private static JsonNode parse(String body) {
		try {
			return MAPPER.readTree(body);
		} catch (Exception e) {
			throw new IllegalStateException("Unable to parse Razorpay response: " + body, e);
		}
	}

}
