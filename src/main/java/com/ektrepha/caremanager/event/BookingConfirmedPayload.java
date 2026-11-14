package com.ektrepha.caremanager.event;

import java.time.Instant;

/** outbox_events.type = "BookingConfirmed" — only emitted for a parent's first booking. */
public record BookingConfirmedPayload(Long bookingId, Long parentId, Instant visitStartsAt) {
}
