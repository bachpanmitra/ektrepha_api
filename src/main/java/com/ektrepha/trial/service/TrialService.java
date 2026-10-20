package com.ektrepha.trial.service;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import com.ektrepha.model.Booking;
import com.ektrepha.model.User;
import com.ektrepha.trial.dto.response.TrialStatusResponse;

/** FIRST5_FEE_WAIVER — see {@code com.ektrepha.trial.impl.TrialServiceImpl} for the implementation and the plan doc for the full design. */
public interface TrialService {

	/** Called from AuthServiceImpl#verifyMobileOtp only when isNewUser - runs eligibility and creates the ACTIVE/INELIGIBLE row. No-ops if app.trial.enabled=false. */
	void onFirstOtpVerification(User user, String deviceId);

	/** GET /parents/me's trial block. Null if this parent has no trial row at all. */
	TrialStatusResponse snapshotFor(Long parentId);

	/**
	 * Whether a quote for this parent+careDate should have its caregiver rate + platform fee
	 * waived — empty if no ACTIVE trial applies. GST ({@code gstPaise}) is deliberately never
	 * waived: it's the only thing the parent still pays during an active trial. There is no
	 * partial/capped discount: while the trial is ACTIVE, every eligible booking's subtotal+fee is
	 * 100% free regardless of its own hours; the free-hours cap only governs when the trial ends
	 * (see {@link #settleBookingCheckout}), never how much of any single booking is charged.
	 */
	Optional<TrialQuoteApplication> evaluateForQuote(Long parentId, long subtotalPaise, long feePaise, long gstPaise, LocalDate careDate);

	/**
	 * Pre-charge guard, called from HourlyCareServiceImpl#createBooking only when the quote being
	 * confirmed carried a trial discount (quote.trialId != null) - the one abuse signal (duplicate
	 * care address) this flow can still catch before any payment order exists. Throws
	 * TrialRevokedException (never exposing which check failed) if this address is already claimed
	 * by another trial, after revoking trialId itself; otherwise claims the address for trialId if
	 * it hasn't claimed one yet.
	 */
	void guardAddressOrRevoke(UUID trialId, String rawAddress);

	/**
	 * Called once payment is SUCCESS for a booking that carried a trial discount
	 * (booking.getTrialId() != null) - this app's actual "checkout completed" moment. Locks the
	 * trial row, adds this booking's hours (startTime..endTime) to freeHoursUsed, runs the
	 * payment-fingerprint abuse check (best-effort - money has already moved by this point), and
	 * ends the trial (CAP_HOURS) if freeHoursUsed has now reached/passed the cap - the booking that
	 * crossed it was still fully honored free.
	 */
	void settleBookingCheckout(Booking booking, String providerReference);

	/** TrialExpiryJob's per-row call — ends an ACTIVE trial past its endsAt. Re-checks status itself, since it may have changed between the sweep's query and this call. */
	void expireIfStillActive(UUID trialId);

	record TrialQuoteApplication(UUID trialId, long discountPaise) {
	}

}
