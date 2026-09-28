package com.ektrepha.payment.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ektrepha.model.Booking;
import com.ektrepha.model.BookingStatus;
import com.ektrepha.model.PaymentStatus;
import com.ektrepha.model.PaymentTransaction;
import com.ektrepha.payment.service.PaymentGatewayService;
import com.ektrepha.repository.BookingRepository;
import com.ektrepha.repository.PaymentTransactionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Server-to-server source of truth for a payment's outcome — unlike the client's own confirm call
 * (see {@code HourlyCareController#confirmPayment}), this can't be skipped by a client that pays
 * then never calls back (app killed, network drop). Registered {@code permitAll} in
 * {@code SecurityConfig}; the Razorpay signature on the raw body is the only auth this endpoint has,
 * so verification is mandatory, not optional.
 * <p>
 * Only advances {@link BookingStatus#AWAITING_PAYMENT} -> {@link BookingStatus#ASSIGNING_CAREGIVER}
 * today, matching the one pay-first flow that exists (hourly-care). A future pay-first booking flow
 * with a different post-payment transition needs this generalized, not just called into.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/payments/webhook")
@RequiredArgsConstructor
public class RazorpayWebhookController {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private final PaymentGatewayService paymentGatewayService;
	private final PaymentTransactionRepository paymentTransactionRepository;
	private final BookingRepository bookingRepository;

	@PostMapping("/razorpay")
	@Transactional
	public ResponseEntity<Void> handle(@RequestBody String payload,
			@RequestHeader(value = "X-Razorpay-Signature", required = false) String signature) {
		if (!paymentGatewayService.verifyWebhookSignature(payload, signature)) {
			log.warn("Razorpay webhook signature verification failed");
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
		}

		JsonNode root;
		try {
			root = MAPPER.readTree(payload);
		} catch (Exception e) {
			log.error("Unable to parse Razorpay webhook payload", e);
			return ResponseEntity.ok().build();
		}

		String event = root.path("event").asText("");
		JsonNode paymentEntity = root.path("payload").path("payment").path("entity");
		String orderId = paymentEntity.path("order_id").asText(null);
		String paymentId = paymentEntity.path("id").asText(null);
		if (orderId == null) {
			return ResponseEntity.ok().build();
		}

		PaymentTransaction transaction = paymentTransactionRepository.findByProviderReference(orderId).orElse(null);
		// Unknown order id, or already settled by a prior webhook delivery/the client's own confirm
		// call - Razorpay retries webhooks, so this must stay a no-op rather than an error.
		if (transaction == null || transaction.getStatus() != PaymentStatus.INITIATED) {
			return ResponseEntity.ok().build();
		}

		if ("payment.captured".equals(event)) {
			transaction.setStatus(PaymentStatus.SUCCESS);
			transaction.setGatewayPaymentId(paymentId);
			paymentTransactionRepository.save(transaction);
			advanceBooking(transaction.getBooking());
			log.info("Razorpay webhook confirmed payment: bookingId={}, paymentId={}, razorpayPaymentId={}",
					transaction.getBooking().getId(), transaction.getId(), paymentId);
		} else if ("payment.failed".equals(event)) {
			transaction.setStatus(PaymentStatus.FAILED);
			transaction.setGatewayPaymentId(paymentId);
			paymentTransactionRepository.save(transaction);
			log.info("Razorpay webhook reported failed payment: bookingId={}, paymentId={}", transaction.getBooking().getId(), transaction.getId());
		}
		return ResponseEntity.ok().build();
	}

	private void advanceBooking(Booking booking) {
		if (booking.getStatus() == BookingStatus.AWAITING_PAYMENT) {
			booking.setStatus(BookingStatus.ASSIGNING_CAREGIVER);
			bookingRepository.save(booking);
		}
	}

}
