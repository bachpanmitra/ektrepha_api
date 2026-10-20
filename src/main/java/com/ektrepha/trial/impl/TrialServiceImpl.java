package com.ektrepha.trial.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.ektrepha.config.properties.AppProperties;
import com.ektrepha.exception.TrialRevokedException;
import com.ektrepha.model.Booking;
import com.ektrepha.model.Parent;
import com.ektrepha.model.ParentTrial;
import com.ektrepha.model.TrialEndedReason;
import com.ektrepha.model.TrialStatus;
import com.ektrepha.model.User;
import com.ektrepha.parent.impl.ParentResolver;
import com.ektrepha.payment.service.PaymentGatewayService;
import com.ektrepha.repository.BookingRepository;
import com.ektrepha.repository.ParentRepository;
import com.ektrepha.repository.ParentTrialRepository;
import com.ektrepha.trial.TrialHashUtil;
import com.ektrepha.trial.dto.response.TrialStatusResponse;
import com.ektrepha.trial.service.TrialAnalyticsService;
import com.ektrepha.trial.service.TrialService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class TrialServiceImpl implements TrialService {

	private static final ZoneId INDIA_ZONE = ZoneId.of("Asia/Kolkata");

	private final ParentTrialRepository parentTrialRepository;
	private final ParentRepository parentRepository;
	private final BookingRepository bookingRepository;
	private final ParentResolver parentResolver;
	private final PaymentGatewayService paymentGatewayService;
	private final TrialAnalyticsService analyticsService;
	private final TrialEmailServiceImpl trialEmailService;
	private final AppProperties appProperties;
	private final PlatformTransactionManager transactionManager;

	@Override
	public void onFirstOtpVerification(User user, String deviceId) {
		if (!appProperties.trial().enabled()) {
			return;
		}
		// Own REQUIRES_NEW transaction, same technique as AuthServiceImpl#createMobileUser - the
		// unique parent_id constraint is the real race guard against two concurrent first-verify
		// calls for the same (brand-new) phone both reaching this method; a Postgres constraint
		// violation aborts the whole physical transaction it happened in, so this can't share the
		// caller's (verifyMobileOtp's) transaction.
		TransactionTemplate requiresNew = new TransactionTemplate(transactionManager);
		requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		try {
			requiresNew.executeWithoutResult(status -> createTrialRow(user, deviceId));
		} catch (DataIntegrityViolationException e) {
			log.info("Trial row already exists for userId={} (concurrent first verify), ignoring", user.getId());
		}
	}

	private void createTrialRow(User user, String deviceId) {
		Parent parent = parentResolver.resolveOrCreate(user.getId());
		String salt = appProperties.trial().hashSalt();
		String phoneHash = TrialHashUtil.sha256Hex(user.getPhone(), salt);
		String deviceHash = TrialHashUtil.sha256Hex(deviceId, salt);

		boolean phoneReused = parentTrialRepository.existsByPhoneHash(phoneHash);
		boolean deviceReused = deviceHash != null && parentTrialRepository.existsByDeviceIdHash(deviceHash);
		boolean hasPastBookings = bookingRepository.countByParentId(parent.getId()) > 0;

		Instant now = Instant.now();
		ParentTrial.ParentTrialBuilder builder = ParentTrial.builder()
				.parentId(parent.getId())
				.phoneHash(phoneHash)
				.deviceIdHash(deviceHash)
				.startedAt(now);

		if (phoneReused || deviceReused || hasPastBookings) {
			ParentTrial trial = builder.status(TrialStatus.INELIGIBLE).endsAt(now).build();
			parentTrialRepository.save(trial);
			String reason = phoneReused ? "PHONE_REUSED" : deviceReused ? "DEVICE_REUSED" : "HAS_PAST_BOOKINGS";
			analyticsService.track("trial_ineligible", Map.of("parentId", parent.getId(), "reason", reason));
		} else {
			Instant endsAt = now.plus(Duration.ofMinutes(appProperties.trial().windowMinutes()));
			ParentTrial trial = builder.status(TrialStatus.ACTIVE).endsAt(endsAt).build();
			parentTrialRepository.save(trial);
			analyticsService.track("trial_started", Map.of("parentId", parent.getId(), "endsAt", endsAt.toString()));
		}
	}

	@Override
	@Transactional(readOnly = true)
	public TrialStatusResponse snapshotFor(Long parentId) {
		return parentTrialRepository.findByParentId(parentId).map(this::toSnapshot).orElse(null);
	}

	private TrialStatusResponse toSnapshot(ParentTrial trial) {
		return new TrialStatusResponse(trial.getStatus().name(), trial.getEndsAt(), Instant.now(),
				trial.getFreeHoursUsed(), appProperties.trial().freeHoursCap());
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<TrialQuoteApplication> evaluateForQuote(Long parentId, long subtotalPaise, long feePaise, long gstPaise, LocalDate careDate) {
		if (!appProperties.trial().enabled()) {
			return Optional.empty();
		}
		Optional<ParentTrial> maybeTrial = parentTrialRepository.findByParentId(parentId);
		if (maybeTrial.isEmpty()) {
			return Optional.empty();
		}
		ParentTrial trial = maybeTrial.get();
		// Design decision: trial-window eligibility is decided by BOOKING date (the moment this
		// quote/booking is created, i.e. Instant.now()), never by the booking's own care/service
		// date (careDate below is only used for the separate care-date-limit check). A booking
		// made 10 minutes before a 5-day trial window closes is fully discounted even if the care
		// date itself is a week out; a booking made 10 minutes after the window closes gets no
		// discount even for care happening tonight. This matches how the discount is actually
		// settled: settleBookingCheckout runs at checkout (now), not at care delivery (later), and
		// it's simpler for support to reason about ("was the trial active when you booked?") than
		// a service-date rule would be, with no later re-evaluation as the care date approaches.
		if (trial.getStatus() != TrialStatus.ACTIVE || trial.getEndsAt().isBefore(Instant.now())) {
			return Optional.empty();
		}
		LocalDate today = LocalDate.now(INDIA_ZONE);
		if (ChronoUnit.DAYS.between(today, careDate) > appProperties.trial().careDateLimitDays()) {
			return Optional.empty();
		}
		// The caregiver rate (subtotalPaise) and this platform's own commission (feePaise) are
		// both waived in full - GST (gstPaise) is the only thing the parent still pays during an
		// active trial. No partial/capped waiver: while ACTIVE, every eligible booking's
		// subtotal+fee is waived in full regardless of its own hours. freeHoursCap only governs
		// when the trial ends (settleBookingCheckout), never how much of any single booking is charged.
		long discountPaise = subtotalPaise + feePaise;
		if (discountPaise <= 0) {
			return Optional.empty();
		}
		return Optional.of(new TrialQuoteApplication(trial.getId(), discountPaise));
	}

	@Override
	@Transactional
	public void guardAddressOrRevoke(UUID trialId, String rawAddress) {
		ParentTrial trial = parentTrialRepository.findByIdForUpdate(trialId).orElse(null);
		if (trial == null || trial.getStatus() != TrialStatus.ACTIVE) {
			// Already settled/expired/revoked by something else between quote and this reserve call
			// — nothing to guard; the discount this quote carried simply won't be honored (the caller
			// re-derives that from the trial's current status, not from this method).
			return;
		}
		String hash = TrialHashUtil.sha256Hex(rawAddress, appProperties.trial().hashSalt());
		if (parentTrialRepository.existsByAddressHashAndIdNot(hash, trialId)) {
			endTrialAndNotify(trial, TrialStatus.REVOKED, TrialEndedReason.ABUSE);
			throw new TrialRevokedException("This trial can no longer be applied to this booking");
		}
		if (trial.getAddressHash() == null) {
			trial.setAddressHash(hash);
			parentTrialRepository.save(trial);
		}
	}

	@Override
	@Transactional
	public void settleBookingCheckout(Booking booking, String providerReference) {
		UUID trialId = booking.getTrialId();
		if (trialId == null) {
			return;
		}
		ParentTrial trial = parentTrialRepository.findByIdForUpdate(trialId).orElse(null);
		if (trial == null) {
			log.warn("bookingId={} references trialId={} that no longer exists — skipping trial settlement", booking.getId(), trialId);
			return;
		}

		BigDecimal bookingHours = hoursBetween(booking.getStartTime(), booking.getEndTime());
		trial.setFreeHoursUsed(trial.getFreeHoursUsed().add(bookingHours));

		// providerReference is null when a booking was waived down to ₹0 and never had a real
		// Razorpay order (see HourlyCareServiceImpl#initiatePayment) - nothing to fingerprint then.
		String fingerprintHash = providerReference == null
				? null
				: paymentGatewayService.fetchPaymentFingerprint(providerReference)
						.map(raw -> TrialHashUtil.sha256Hex(raw, appProperties.trial().hashSalt()))
						.orElse(null);
		// Best-effort, post-hoc: Razorpay auto-captures before this fingerprint is knowable, so this
		// booking's discounted charge already happened and is never clawed back — a match only
		// revokes the trial for every *future* booking. See plan doc Context #1.
		boolean abuse = fingerprintHash != null && parentTrialRepository.existsByPaymentFingerprintAndIdNot(fingerprintHash, trialId);

		if (trial.getStatus() == TrialStatus.ACTIVE) {
			if (abuse) {
				endTrialAndNotify(trial, TrialStatus.REVOKED, TrialEndedReason.ABUSE);
			} else {
				if (fingerprintHash != null && trial.getPaymentFingerprint() == null) {
					trial.setPaymentFingerprint(fingerprintHash);
				}
				if (trial.getFreeHoursUsed().compareTo(appProperties.trial().freeHoursCap()) >= 0) {
					endTrialAndNotify(trial, TrialStatus.ENDED, TrialEndedReason.CAP_HOURS);
				} else {
					parentTrialRepository.save(trial);
				}
			}
		} else {
			// Already ended/revoked by something else (e.g. the expiry job) between reserve and this
			// confirm — still record the usage this already-locked-in booking represents.
			parentTrialRepository.save(trial);
		}

		analyticsService.track("trial_booking_confirmed", Map.of(
				"bookingId", booking.getId(),
				"discountPaise", booking.getTrialDiscountPaise(),
				"serviceType", booking.getServiceType().getCode()));
	}

	@Override
	@Transactional
	public void expireIfStillActive(UUID trialId) {
		ParentTrial trial = parentTrialRepository.findByIdForUpdate(trialId).orElse(null);
		if (trial == null || trial.getStatus() != TrialStatus.ACTIVE || trial.getEndsAt().isAfter(Instant.now())) {
			return;
		}
		endTrialAndNotify(trial, TrialStatus.ENDED, TrialEndedReason.EXPIRED);
	}

	private void endTrialAndNotify(ParentTrial trial, TrialStatus newStatus, TrialEndedReason reason) {
		trial.setStatus(newStatus);
		trial.setEndedReason(reason);
		trial = parentTrialRepository.save(trial);

		analyticsService.track("trial_ended", Map.of(
				"parentId", trial.getParentId(),
				"endedReason", reason.name(),
				"freeHoursUsed", trial.getFreeHoursUsed()));

		if (trial.getFreeHoursUsed().compareTo(BigDecimal.ZERO) > 0 && trial.getEndedEmailSentAt() == null) {
			sendEndedEmail(trial);
			trial.setEndedEmailSentAt(Instant.now());
			parentTrialRepository.save(trial);
		}
	}

	private void sendEndedEmail(ParentTrial trial) {
		parentRepository.findByIdWithUser(trial.getParentId()).ifPresent(parent -> {
			String email = parent.getUser() != null ? parent.getUser().getEmail() : null;
			trialEmailService.sendTrialEndedEmail(email, parent.getFirstName(), trial.getFreeHoursUsed());
		});
	}

	private static BigDecimal hoursBetween(Instant start, Instant end) {
		long minutes = Duration.between(start, end).toMinutes();
		return BigDecimal.valueOf(minutes).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
	}

}
