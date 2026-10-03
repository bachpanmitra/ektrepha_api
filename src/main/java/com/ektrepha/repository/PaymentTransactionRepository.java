package com.ektrepha.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import com.ektrepha.model.PaymentStatus;
import com.ektrepha.model.PaymentTransaction;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {

	Optional<PaymentTransaction> findByIdAndBookingParentId(Long id, Long parentId);

	// The active attempt to confirm/fail against - the most recent one still awaiting a gateway result.
	Optional<PaymentTransaction> findFirstByBookingIdAndStatusOrderByIdDesc(Long bookingId, PaymentStatus status);

	// Locked variant for HourlyCareServiceImpl#reconcileStalePayment only - that path races against
	// the client's own confirmPayment call (app confirms, then immediately polls /status; the poll
	// can land mid-confirm). A plain read here let both paths see INITIATED and independently mark
	// the same transaction SUCCESS, double-writing order_activity (seen live: booking 1322 logged
	// PAYMENT_SUCCEEDED twice, ~0.6s apart, one tagged "reconciled"). confirmPayment's own save()
	// takes an UPDATE row lock regardless, so FOR UPDATE here is enough to serialize against it: the
	// second caller blocks until the first commits, then re-reads status=SUCCESS and the WHERE no
	// longer matches - standard Postgres FOR UPDATE re-check semantics, not a cosmetic dedup.
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select t from PaymentTransaction t where t.booking.id = :bookingId and t.status = :status order by t.id desc")
	Optional<PaymentTransaction> findFirstByBookingIdAndStatusOrderByIdDescForUpdate(@Param("bookingId") Long bookingId, @Param("status") PaymentStatus status);

	// RazorpayWebhookController resolves the transaction a webhook event is about by the Razorpay
	// order id it was given at initiatePayment - see PaymentTransaction#providerReference.
	Optional<PaymentTransaction> findByProviderReference(String providerReference);

	// Admin booking detail's payment status — the latest attempt regardless of outcome (most bookings
	// outside the hourly-care pay-first flow have none at all, hence Optional).
	Optional<PaymentTransaction> findFirstByBookingIdOrderByIdDesc(Long bookingId);

}
