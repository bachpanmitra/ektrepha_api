package com.ektrepha.trial.impl;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.ektrepha.model.Parent;
import com.ektrepha.model.ParentTrial;
import com.ektrepha.model.TrialStatus;
import com.ektrepha.notification.service.PushSenderService;
import com.ektrepha.repository.ParentRepository;
import com.ektrepha.repository.ParentTrialRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Every 5 minutes: the FIRST5_FEE_WAIVER trial's two reminder pushes — day 2 at 10:00 IST, and
 * 24h before {@code ends_at} — each skipped once the parent has already made a trial booking, and
 * neither ever sent between 21:00–08:00 IST (a push whose window falls inside quiet hours is
 * simply retried on the next tick, every 5 minutes, until it's outside quiet hours). Same per-row
 * try/catch shape as {@code BookingStaleAutoCancelJob}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TrialNotificationJob {

	private static final ZoneId INDIA_ZONE = ZoneId.of("Asia/Kolkata");
	private static final int QUIET_HOURS_START = 21;
	private static final int QUIET_HOURS_END = 8;

	private final ParentTrialRepository parentTrialRepository;
	private final ParentRepository parentRepository;
	private final PushSenderService pushSenderService;

	@Scheduled(cron = "${app.trial.notification-cron:0 */5 * * * *}")
	public void sendDueReminders() {
		Instant now = Instant.now();

		for (ParentTrial trial : parentTrialRepository.findByStatusAndFreeHoursUsedAndDay2PushSentAtIsNull(TrialStatus.ACTIVE, BigDecimal.ZERO)) {
			try {
				maybeSendDay2Push(trial, now);
			} catch (RuntimeException e) {
				log.error("Could not send day-2 trial push for trialId={}", trial.getId(), e);
			}
		}

		for (ParentTrial trial : parentTrialRepository.findByStatusAndFreeHoursUsedAndExpiryReminderPushSentAtIsNull(TrialStatus.ACTIVE, BigDecimal.ZERO)) {
			try {
				maybeSendExpiryReminderPush(trial, now);
			} catch (RuntimeException e) {
				log.error("Could not send expiry-reminder trial push for trialId={}", trial.getId(), e);
			}
		}
	}

	private void maybeSendDay2Push(ParentTrial trial, Instant now) {
		LocalDate startDateIst = LocalDate.ofInstant(trial.getStartedAt(), INDIA_ZONE);
		Instant target = startDateIst.plusDays(1).atTime(10, 0).atZone(INDIA_ZONE).toInstant();
		if (now.isBefore(target)) {
			return;
		}
		sendPush(trial, "Your free trial is waiting", "You've got fee-free bookings left on your Ektrepha trial — book your first one today.");
		trial.setDay2PushSentAt(now);
		parentTrialRepository.save(trial);
	}

	private void maybeSendExpiryReminderPush(ParentTrial trial, Instant now) {
		Instant target = trial.getEndsAt().minusSeconds(24 * 3600);
		if (now.isBefore(target) || !now.isBefore(trial.getEndsAt())) {
			return;
		}
		if (isQuietHours(now)) {
			return; // retried on the next 5-minute tick until outside quiet hours
		}
		sendPush(trial, "Your free trial ends soon", "Your Ektrepha fee-waiver trial ends in less than 24 hours — book now to use it.");
		trial.setExpiryReminderPushSentAt(now);
		parentTrialRepository.save(trial);
	}

	private void sendPush(ParentTrial trial, String title, String body) {
		Parent parent = parentRepository.findByIdWithUser(trial.getParentId()).orElse(null);
		if (parent == null || parent.getUser() == null) {
			return;
		}
		pushSenderService.sendToUser(parent.getUser().getId(), title, body);
	}

	private boolean isQuietHours(Instant now) {
		int hour = ZonedDateTime.ofInstant(now, INDIA_ZONE).getHour();
		return hour >= QUIET_HOURS_START || hour < QUIET_HOURS_END;
	}

}
