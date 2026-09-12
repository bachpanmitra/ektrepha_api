package com.ektrepha.pricing.impl;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.ektrepha.pricing.service.DemandPricingService;

import lombok.RequiredArgsConstructor;

/**
 * Thin scheduled wrapper, same shape as {@code VerificationDriftAuditJob} - runs on one global
 * cadence (default 10 min) rather than each zone's own {@code recompute_interval_mins}; honoring a
 * per-row interval would need a dynamic {@code TaskScheduler} trigger, which isn't built for v1
 * (see the design doc's "10 min, or read interval per config row" either/or).
 */
@Component
@RequiredArgsConstructor
public class DemandSnapshotRecomputeJob {

	private final DemandPricingService demandPricingService;

	@Scheduled(fixedRateString = "${app.pricing.demand-recompute-interval-ms:600000}")
	public void recompute() {
		demandPricingService.recomputeAll();
	}

}
