package com.ektrepha.payment.service;

import java.math.BigDecimal;
import java.util.Optional;

import com.ektrepha.payment.dto.GatewayOrder;
import com.ektrepha.payment.dto.GatewayPaymentStatus;

/**
 * Razorpay integration, shared by every pay-first flow in the parent app (currently hourly-care;
 * see {@code com.ektrepha.hourlycare}). Kept gateway-agnostic at the interface level so a future
 * provider swap doesn't touch callers.
 */
public interface PaymentGatewayService {

	/** Creates a gateway order for {@code amount} (rupees) that the client's Checkout will pay against. */
	GatewayOrder createOrder(BigDecimal amount, String receipt);

	/** Verifies the signature Razorpay Checkout hands the client back after a successful charge. */
	boolean verifyPaymentSignature(String orderId, String paymentId, String signature);

	/** Verifies the {@code X-Razorpay-Signature} header on an incoming webhook call against its raw body. */
	boolean verifyWebhookSignature(String payload, String signatureHeader);

	/**
	 * Looks up whatever Razorpay currently knows about payments against {@code orderId} - a captured
	 * payment, a failed one, or nothing yet (empty). Used to reconcile a transaction stuck at
	 * INITIATED without waiting on the client's own confirm call or a webhook delivery.
	 */
	Optional<GatewayPaymentStatus> findLatestPayment(String orderId);

}
