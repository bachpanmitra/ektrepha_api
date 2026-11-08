package com.ektrepha.workforce.dto.response;

/** Nested inside {@link StartTripResponse} — null when the nanny's location isn't known yet. */
public record EtaView(long durationSec, long distanceM) {
}
