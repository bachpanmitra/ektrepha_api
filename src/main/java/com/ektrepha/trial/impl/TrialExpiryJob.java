package com.ektrepha.trial.impl;

import java.time.Instant;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.ektrepha.model.ParentTrial;
import com.ektrepha.model.TrialStatus;
import com.ektrepha.repository.ParentTrialRepository;
import com.ektrepha.trial.service.TrialService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Every 5 minutes: ACTIVE FIRST5_FEE_WAIVER trials whose {@code ends_at} has passed become
 * ENDED/EXPIRED. Same per-row try/catch shape as {@code BookingStaleAutoCancelJob} — one row's
 * failure doesn't stop the sweep, and {@link TrialService#expireIfStillActive} re-checks each
 * trial's status for itself since it may have changed between this query and that call.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TrialExpiryJob {

	private final ParentTrialRepository parentTrialRepository;
	private final TrialService trialService;

	@Scheduled(cron = "${app.trial.expiry-cron:0 */5 * * * *}")
	public void expireDueTrials() {
		List<ParentTrial> dueTrials = parentTrialRepository.findByStatusAndEndsAtBefore(TrialStatus.ACTIVE, Instant.now());
		for (ParentTrial trial : dueTrials) {
			try {
				trialService.expireIfStillActive(trial.getId());
			} catch (RuntimeException e) {
				log.error("Could not expire trialId={}", trial.getId(), e);
			}
		}
	}

}
