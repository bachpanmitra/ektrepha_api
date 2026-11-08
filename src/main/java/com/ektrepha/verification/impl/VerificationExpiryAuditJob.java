package com.ektrepha.verification.impl;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.ektrepha.model.NannyVerificationStatus;
import com.ektrepha.notification.service.PushSenderService;
import com.ektrepha.repository.NannyRepository;
import com.ektrepha.repository.NannyVerificationRepository;
import com.ektrepha.verification.service.NannyVerificationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * PRD: "Periodic re-verification (e.g. every 12 months) and auto-suspension when documents
 * expire." Two independent sweeps, both via {@link NannyVerificationService#changeStatus} (never
 * the recompute path, since that's only ever triggered by a signal actually changing):
 * <ol>
 * <li>Any currently-APPROVED nanny whose latest VERIFIED PCC has expired -&gt; SUSPENDED. This is
 * the safety-critical one - run daily.</li>
 * <li>Any currently-APPROVED nanny whose last approval is &gt;= 12 months old -&gt; UNDER_REVIEW
 * (not SUSPENDED - due for a check, not necessarily unsafe yet), so they re-enter the normal
 * document/reference/interview/training review cycle.</li>
 * </ol>
 * Both run with {@code changedByUserId = null} (a system transition, not an admin one) - see
 * {@code nanny_status_history.changed_by} being nullable for exactly this case.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VerificationExpiryAuditJob {

	// Approximated as 365 days rather than Period.ofMonths(12) - Instant has no calendar-month
	// arithmetic, and a +/- few-day fuzz on a 12-month re-verification window is immaterial.
	private static final long RE_VERIFICATION_WINDOW_DAYS = 365;

	private final NannyVerificationRepository nannyVerificationRepository;
	private final NannyRepository nannyRepository;
	private final NannyVerificationService nannyVerificationService;
	private final PushSenderService pushSenderService;

	@Scheduled(cron = "${app.verification.expiry-audit-cron:0 0 4 * * *}")
	public void auditExpiryAndReverification() {
		suspendExpiredPcc();
		flagDueForReverification();
		remindBeforeExpiry();
	}

	private void suspendExpiredPcc() {
		List<Long> nannyIds = nannyVerificationRepository.findNannyIdsWithExpiredVerifiedPcc();
		for (Long nannyId : nannyIds) {
			try {
				nannyVerificationService.changeStatus(nannyId, NannyVerificationStatus.SUSPENDED,
						"Police Clearance Certificate expired - automatic suspension pending a renewed document", null);
				log.warn("Nanny auto-suspended: nannyId={}, reason=PCC expired", nannyId);
			} catch (RuntimeException e) {
				// A nanny who is already SUSPENDED/BANNED, or whose status changed between the query
				// and this call, must not stop the rest of the sweep from running.
				log.error("Could not auto-suspend nannyId={} for PCC expiry", nannyId, e);
			}
		}
	}

	// "Push to nanny and alert ops 30 days before expiresOn" (API Map row 26). Ops alert is a log
	// line, not a real broadcast channel - same stand-in NannyAttendanceServiceImpl's OTP-lock
	// alert uses, until a real ops-broadcast exists.
	private void remindBeforeExpiry() {
		for (Object[] row : nannyVerificationRepository.findDueForExpiryReminderRows()) {
			Long verificationId = ((Number) row[0]).longValue();
			Long nannyUserId = ((Number) row[1]).longValue();
			LocalDate expiryDate = (LocalDate) row[2];
			try {
				pushSenderService.sendToUser(nannyUserId, "Your Police Clearance Certificate expires soon",
						"Your PCC expires on " + expiryDate + ". Upload a renewed certificate to avoid an automatic suspension.");
				log.warn("PCC expiring soon: verificationId={}, nannyUserId={}, expiryDate={}", verificationId, nannyUserId, expiryDate);
				nannyVerificationService.markExpiryReminderSent(verificationId);
			} catch (RuntimeException e) {
				log.error("Could not send expiry reminder for verificationId={}", verificationId, e);
			}
		}
	}

	private void flagDueForReverification() {
		Instant cutoff = Instant.now().minus(RE_VERIFICATION_WINDOW_DAYS, ChronoUnit.DAYS);
		List<Long> nannyIds = nannyRepository.findIdsByStatusAndStatusChangedAtBefore(NannyVerificationStatus.APPROVED, cutoff);
		for (Long nannyId : nannyIds) {
			try {
				nannyVerificationService.changeStatus(nannyId, NannyVerificationStatus.UNDER_REVIEW,
						"Periodic re-verification due (12 months since last approval)", null);
				log.info("Nanny flagged for periodic re-verification: nannyId={}", nannyId);
			} catch (RuntimeException e) {
				log.error("Could not flag nannyId={} for periodic re-verification", nannyId, e);
			}
		}
	}

}
