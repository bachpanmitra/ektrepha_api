package com.ektrepha.caremanager.event;

/** outbox_events.type = "NannyCheckedOut" — only emitted for a parent's first booking. */
public record NannyCheckedOutPayload(Long bookingId, Long parentId) {
}
