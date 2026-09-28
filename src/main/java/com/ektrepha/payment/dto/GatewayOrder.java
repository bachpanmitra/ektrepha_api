package com.ektrepha.payment.dto;

import java.math.BigDecimal;

/**
 * A created Razorpay order, as {@link com.ektrepha.payment.service.PaymentGatewayService#createOrder}
 * returns it. {@code keyId} is Razorpay's public key id — safe to hand to the client, which needs it
 * to open Razorpay Checkout against {@code orderId}.
 */
public record GatewayOrder(String orderId, String keyId, BigDecimal amount, String currency) {
}
