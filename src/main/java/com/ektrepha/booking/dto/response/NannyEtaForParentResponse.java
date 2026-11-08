package com.ektrepha.booking.dto.response;

import java.time.Instant;

/** GET /bookings/{id}/nanny-eta (parent-side, API Map row 62) — only returned while the booking is ON_THE_WAY; once the nanny has arrived or checked in, her location is never sent to the parent again (privacy). */
public record NannyEtaForParentResponse(
		String status,
		long distanceM,
		long durationSec,
		Instant etaAt,
		Double nannyLat,
		Double nannyLng,
		Instant updatedAt,
		boolean stale) {
}
