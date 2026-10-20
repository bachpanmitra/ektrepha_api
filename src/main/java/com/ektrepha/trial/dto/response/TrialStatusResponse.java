package com.ektrepha.trial.dto.response;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * {@code GET /parents/me}'s {@code trial} block. Every eligible booking made while {@code status
 * == "ACTIVE"} has its caregiver rate + platform fee waived in full regardless of length (GST is
 * the only thing the parent still pays) — there is no remaining-allowance figure to report, only
 * the cumulative {@code hoursUsed}/{@code hoursCap}, meaningful in every status (including after the
 * trial has ended, for the app's trial-ended summary).
 */
public record TrialStatusResponse(
		String status,
		Instant endsAt,
		Instant serverNow,
		BigDecimal hoursUsed,
		BigDecimal hoursCap) {
}
