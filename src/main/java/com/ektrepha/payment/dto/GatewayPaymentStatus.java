package com.ektrepha.payment.dto;

/**
 * What Razorpay currently knows about payments against an order, as
 * {@link com.ektrepha.payment.service.PaymentGatewayService#findLatestPayment} returns it - used to
 * recover a transaction stuck at {@code INITIATED} when the client's own Checkout confirm call never
 * arrives (e.g. a UPI app-switch to Google Pay/PhonePe/etc. that never hands control back to the
 * app) and no webhook is registered yet to catch it server-to-server.
 */
public record GatewayPaymentStatus(String paymentId, boolean captured) {
}
