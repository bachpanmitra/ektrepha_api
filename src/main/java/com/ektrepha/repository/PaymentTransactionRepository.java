package com.ektrepha.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ektrepha.model.PaymentStatus;
import com.ektrepha.model.PaymentTransaction;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {

	Optional<PaymentTransaction> findByIdAndBookingParentId(Long id, Long parentId);

	// The active attempt to confirm/fail against - the most recent one still awaiting a gateway result.
	Optional<PaymentTransaction> findFirstByBookingIdAndStatusOrderByIdDesc(Long bookingId, PaymentStatus status);

	// RazorpayWebhookController resolves the transaction a webhook event is about by the Razorpay
	// order id it was given at initiatePayment - see PaymentTransaction#providerReference.
	Optional<PaymentTransaction> findByProviderReference(String providerReference);

}
