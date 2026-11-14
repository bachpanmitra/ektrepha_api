package com.ektrepha.caremanager.event;

/** outbox_events.type = "NannyCheckedIn" — only emitted for a parent's first booking. */
public record NannyCheckedInPayload(Long bookingId, Long parentId) {
}
