package com.ektrepha.caremanager.event;

/** outbox_events.type = "BookingCancelledByNanny" or "NannyNoShow" — both map to REPLACEMENT_NEEDED. */
public record BookingNeedsReplacementPayload(Long bookingId, Long parentId) {
}
