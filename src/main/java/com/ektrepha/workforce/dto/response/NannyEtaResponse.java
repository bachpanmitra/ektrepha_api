package com.ektrepha.workforce.dto.response;

import java.time.Instant;

/** GET .../eta (nanny-side, API Map row 52) — null fields mean "not computed", not an error; a null body entirely means 204 (no location yet). */
public record NannyEtaResponse(long distanceM, long durationSec, Instant etaAt, String source, Instant locationUpdatedAt, boolean stale) {
}
