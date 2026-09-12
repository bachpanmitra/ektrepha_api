package com.ektrepha.pricing.dto.response;

import java.math.BigDecimal;
import java.time.Instant;

/** Surfaced even when {@code enabled} affected nothing about the final price — transparency here is what makes a "why was I charged more" support ticket answerable, and it's the data future threshold tuning needs. */
public record DynamicPricingInfoResponse(boolean enabled, BigDecimal demandRatio, BigDecimal multiplier, Instant computedAt) {
}
