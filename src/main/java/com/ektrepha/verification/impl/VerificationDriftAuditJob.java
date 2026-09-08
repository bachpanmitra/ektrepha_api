package com.ektrepha.verification.impl;

import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.ektrepha.verification.service.NannyVerificationService;
import com.ektrepha.verification.service.NannyVerificationService.DriftRecord;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * "Zero incidents... tracked via a scheduled audit query, not just code review" (PRD success
 * metric). Runs daily by default; overridable via app.verification.audit-cron since audit
 * frequency is an ops concern, not a code concern.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VerificationDriftAuditJob {

	private final NannyVerificationService nannyVerificationService;

	@Scheduled(cron = "${app.verification.audit-cron:0 0 3 * * *}")
	public void auditDrift() {
		List<DriftRecord> drift = nannyVerificationService.findDrift();
		if (drift.isEmpty()) {
			log.info("Verification status drift audit: no drift found");
			return;
		}
		for (DriftRecord record : drift) {
			log.warn("Verification status drift: nanny {} stored={} expected={}", record.nannyId(), record.storedStatus(), record.expectedStatus());
		}
	}

}
