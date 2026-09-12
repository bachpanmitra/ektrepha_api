package com.ektrepha.pricing.dto.response;

import java.math.BigDecimal;
import java.time.Instant;

public record DemandSnapshotResponse(
		Long zoneAreaId,
		Long serviceTypeId,
		Integer openBookingRequests,
		Integer availableCaregivers,
		BigDecimal demandRatio,
		BigDecimal computedMultiplier,
		Instant computedAt) {
}
