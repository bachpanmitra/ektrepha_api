package com.ektrepha.pricing.dto.response;

import java.math.BigDecimal;

import com.ektrepha.model.PricingMode;

public record PriceQuoteResponse(
		BigDecimal baseRate,
		PricingMode pricingMode,
		AppliedRuleResponse appliedRule,
		DynamicPricingInfoResponse dynamicPricing,
		BigDecimal combinedMultiplier,
		BigDecimal hours,
		BigDecimal subtotal,
		/**
		 * GST charged on {@link #subtotal} (the care amount itself) — same rate as
		 * {@link #platformFeeGst} ({@code app.pricing.platform-fee-gst-pct}), confirmed by the
		 * business to apply to the care amount as well, not just the platform fee. Added 2026 —
		 * bookings/quotes persisted before this never had a care-amount GST line at all.
		 */
		BigDecimal careGst,
		BigDecimal platformFee,
		/** GST charged on {@link #platformFee}, per {@code app.pricing.platform-fee-gst-pct} — 0 until that's configured non-zero. */
		BigDecimal platformFeeGst,
		/** The configured rate both {@link #careGst} and {@link #platformFeeGst} are computed at, as a percentage (e.g. 18 = 18%) — {@code app.pricing.platform-fee-gst-pct}, parent-agnostic (same value regardless of trial). */
		BigDecimal gstRatePct,
		BigDecimal total,
		String currency,
		/**
		 * Whether {@link #subtotal}/{@link #platformFee} were zeroed by an active
		 * FIRST5_FEE_WAIVER trial (GST is never waived - it's the only thing still charged) —
		 * always false from {@code PricingServiceImpl#calculate} itself (parent-agnostic); set by
		 * the caller that knows the requesting parent (see
		 * {@code HourlyCareServiceImpl#applyDailyCareTrial}) after re-deriving trial eligibility
		 * server-side. Never trust a client-sent flag for this - see
		 * {@code AvailabilityCheckRequest#isTrialPeriod}.
		 */
		boolean platformFeeWaived,
		boolean isTrialPeriod) {
}
